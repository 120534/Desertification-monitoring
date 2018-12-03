package chd.raster.ingest

import java.io.{File, FileFilter}
import java.net.URI

import geotrellis.proj4._
import geotrellis.raster._
import geotrellis.raster.io.geotiff._
import geotrellis.raster.reproject._
import geotrellis.raster.resample._
import geotrellis.spark._
import geotrellis.spark.io._
import geotrellis.spark.io.hadoop.{HadoopGeoTiffRDD, HadoopLayerWriter}
import geotrellis.spark.io.index._
import geotrellis.spark.pyramid._
import geotrellis.spark.tiling._
import geotrellis.util.LazyLogging
import geotrellis.vector.ProjectedExtent
import org.apache.commons.io.filefilter._
import org.apache.hadoop.fs.{FileSystem, GlobFilter, Path}
import org.apache.spark._
import org.apache.spark.rdd._

import scala.math.BigDecimal.RoundingMode


/**
  * @ Author: zds
  * @ Date: 9/26/18 09:51
  * @ Description: 
  */
object IngestLandsatImage extends LazyLogging {

  type LandsatKey = (ProjectedExtent, URI, Int)

  // For each RDD, we're going to include more information in the key, including:
  // - the ProjectedExtent
  // - the URI
  // - the future band value
  def uriToKey(bandIndex: Int): (URI, ProjectedExtent) => LandsatKey = { (uri, pe) =>
    (pe, uri, bandIndex)
  }

  val options =
    HadoopGeoTiffRDD.Options(
      numPartitions = Some(100)
    )
  val numPartitions = 100

  //  val catalogPath = "hdfs://192.168.1.151:8020/aws/LC08/geotrellis-catalog"
  //
  //  val hdfsUrl = "hdfs://192.168.1.151:8020"

  def main(args: Array[String]): Unit = {
    // Setup Spark to use Kryo serializer.
    val conf = new SparkConf()
      .setIfMissing("spark.master", "local[*]")
      .setAppName("Landsat Ingest")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
      .set("spark.kryoserializer.buffer.max", "1gb")

    implicit val sc = new SparkContext(conf)

    implicit val catalogPath = args(0)
    val hdfsUrl = args(1)
    val (layerName, bandsString, imagesString) = (args(2), args(3), args(4))
    // Manually set bands
    //    val bands =
    //      Array[String](
    //        // Red, Green, Blue
    //        "1", "2", "3", "4",
    //        // Near IR
    //        "5", "6", "7"
    //      )
    val bands: Array[String] = bandsString.split(",")
    val images = imagesString.split(",")
    //    val images =
    //      Array[String](
    //        "/aws/LC08/122/031/LC08_L1TP_122031_20140727"
    //      )
    logger info s"Loading geotiff '$layerName' into '$layerName' in catalog '$catalogPath' and bandNum: ${bandsString} ..."

    try {
      run(layerName, images, bands, hdfsUrl)
      // Pause to wait to close the spark context,
      println("Hit enter to exit.")
      readLine()
    } finally {
      sc.stop()
    }
  }

  /** Get file directory path */
  def fullPath(path: String) = new java.io.File(path).getAbsolutePath

  /** Get images band from multiband Tiff file. */
  def findBandTiff(imagePath: String, band: String): String =
    new File(imagePath).listFiles(new WildcardFileFilter(s"*_B${band}.TIF"): FileFilter).toList match {
      case Nil => sys.error(s"Band ${band} not found for image at ${imagePath}")
      case List(f) => {
        println(f.getAbsolutePath)
        f.getAbsolutePath
      }
      case _ => sys.error(s"Multiple files matching band ${band} found for image at ${imagePath}")
    }

  /** Read data from Tiff Landsat File */
  def readBands(imagePath: String, bands: Array[String]): MultibandGeoTiff = {
    val bandTiffs = bands.map { band => SinglebandGeoTiff(findBandTiff(imagePath, band)) }
    val mb = MultibandGeoTiff(ArrayMultibandTile(bandTiffs.map(_.tile)), bandTiffs.head.extent, bandTiffs.head.crs)
    //    mb.write("/home/zds/Documents/mywork/data/TiffPic/renderTiles/mb.TIF")
    mb
  }

  /** Reporject to tiles and save on HDFS disk */
  def run(layerName: String, images: Array[String], bands: Array[String], hdfsUrl: String)(implicit sc: SparkContext, catalogPath: String): Unit = {
    val targetLayoutScheme = ZoomedLayoutScheme(WebMercator, 256) //WebMercator
    // Create tiled RDDs for each
    val tileSets: Seq[(Int, MultibandTileLayerRDD[SpatialKey])] =
    images.foldLeft(Seq[(Int, MultibandTileLayerRDD[SpatialKey])]()) { (acc, image) =>
      val sourceTile = path2peMultibandTileRdd(image, bands.toList, hdfsUrl)
      val (_, tileLayerMetadata: TileLayerMetadata[SpatialKey]) = TileLayerMetadata.fromRDD[ProjectedExtent, MultibandTile, SpatialKey](sourceTile, FloatingLayoutScheme(512))

      val tiled: RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]] =
        sourceTile
          .tileToLayout[SpatialKey](tileLayerMetadata, Tiler.Options(resampleMethod = NearestNeighbor, partitioner = new HashPartitioner(100)))

      val rdd = MultibandTileLayerRDD(tiled, tileLayerMetadata)

      // Reproject to WebMercator
      //        acc :+ rdd.reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))
      acc :+ rdd.reproject(targetLayoutScheme, bufferSize = 30, Reproject.Options(method = Bilinear, errorThreshold = 0))
    }

    val zoom = tileSets.head._1
    val rdd: RDD[(SpatialKey, MultibandTile)] with Metadata[TileLayerMetadata[SpatialKey]] = ContextRDD(
      sc.union(tileSets.map(_._2)),
      tileSets.map(_._2.metadata).reduce(_ merge _)
    )

    // Write to the catalog
    val writer = HadoopLayerWriter(new Path(catalogPath))
    val keyIndex = ZCurveKeyIndexMethod

    val s = System.currentTimeMillis()
    // if layerName already exsits,delete it
//    org.air.ebds.organize.api.deleteLayerFromCatalog(layerName) 找不到这个包

    // Pyramiding up the zoom levels, write our tiles out to the local file system.
    val lastRdd =
      Pyramid.upLevels(rdd, targetLayoutScheme, zoom, Bilinear) { (rdd, zoom) =>
        writer.write(LayerId(layerName, zoom), rdd, keyIndex)
      }
    val e = System.currentTimeMillis()
    logger.info(s"Elapsed time for Pyramid: ${e - s}ms")
    //attributeStore.write(LayerId(layerName, 0), "times", lastRdd.map(_._1.instant).collect.toArray)

    println("Done")
  }

  def path2peMultibandTileRdd(imagePath: String, bandsList: List[String], hdfsUrl: String)(implicit sc: SparkContext) = {
    // We initialize variable multiBands
    var multibands = HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
      hadoopFindBandTiff(imagePath, bandsList.head, hdfsUrl),
      uriToKey(0),
      options)
    // We create a loop that do the union of all bands
    for (i <- 1 to bandsList.length - 1) {
      val band = bandsList(i)
      val bandTile: RDD[((ProjectedExtent, URI, Int), Tile)] = HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
        hadoopFindBandTiff(imagePath, band, hdfsUrl),
        uriToKey(i),
        options)
      multibands = sc.union(multibands, bandTile)
    }
    // Combine all singleBands into one MultiBand
    // Union these together, rearrange the elements so that we'll be able to group by key,
    // group them by key, and the rearrange again to produce multiband tiles.
    val sourceTiles: RDD[(ProjectedExtent, MultibandTile)] = {
      multibands.repartition(numPartitions)
        .map { case ((pe, uri, bandIndex), tile) =>
          // Get the center of the tile, which we will join on
          val (x, y) = (pe.extent.center.x, pe.extent.center.y)
          // Round the center coordinates in case there's any floating point errors
          val center =
            (
              BigDecimal(x).setScale(5, RoundingMode.HALF_UP).doubleValue(),
              BigDecimal(y).setScale(5, RoundingMode.HALF_UP).doubleValue()
            )
          // Get the scene ID from the path
          val sceneId = uri.getPath.split('/').reverse.drop(1).head

          val newKey = (sceneId, center)
          val newValue = (pe, bandIndex, tile)
          (newKey, newValue)
        }
        .map(l => (l._1, List(l._2))).reduceByKey(_ ++ _)
        //.groupByKey()
        .map { case (oldKey, groupedValues) =>
        val projectedExtent = groupedValues.head._1
        val bands = Array.ofDim[Tile](groupedValues.size)
        for ((_, bandIndex, tile) <- groupedValues) {
          bands(bandIndex) = tile
        }
        (projectedExtent, MultibandTile(bands))
      }
    }
    sourceTiles
  }

  def hadoopFindBandTiff(imagePath: String, band: String, hdfsUrl: String)(implicit sc: SparkContext) = {
    val fs = FileSystem.get(new java.net.URI(hdfsUrl), sc.hadoopConfiguration)
    val path: Path = fs.listStatus(new Path(imagePath), new GlobFilter(s"*_B${band}_REF.TIF")).toList match {
      case List(f) => {
        logger.info(f.getPath.toString)
        f.getPath
      }
    }
    path
  }
}
