import java.net.URI

import chd.raster.ml.Classifier.jenks
import geotrellis.raster.io.geotiff.{GeoTiff, SinglebandGeoTiff}
import geotrellis.raster.{DoubleConstantNoDataCellType, MultibandTile, Tile, isData}
import chd.raster.Utils.{createRandomArr, createRandomArray}
import geotrellis.spark.{ContextRDD, MultibandTileLayerRDD, SpatialKey}
import geotrellis.spark.io.hadoop.{HadoopGeoTiffRDD, HadoopGeoTiffReader}
import geotrellis.vector.ProjectedExtent
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.math.BigDecimal.RoundingMode
import scala.util.Random

/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 20:49 2018/11/27
  * @ Description：测试一下，在tile数组中只去抽样来计算自然间断点
  * @ Modified By：
  */
object TestJenks {
  def main(args: Array[String]): Unit = {
    import java.net.URI

    import geotrellis.raster._
    import geotrellis.spark._
    import geotrellis.spark.io.s3._
    import geotrellis.vector._
    import org.apache.spark.SparkContext
    import org.apache.spark.rdd.RDD

    import scala.math.BigDecimal.RoundingMode

    //    implicit val sc: SparkContext =
    //      geotrellis.spark.util.SparkUtils.createLocalSparkContext("local[*]", "AnalysisUsingSpark")

    val spark = SparkSession.builder().master("local[*]")
      .appName("SimpleRegression")
      .getOrCreate()
    implicit val sc = spark.sparkContext

    //传入地表反射率数据
    def bandPath(i: Int) = s"data/input/LC08_L1TP_129028_20160802_20170322_01_T1_sr_band${i}.tif"

    import geotrellis.spark.io.hadoop._

//    val blueRDD: RDD[(ProjectedExtent, Tile)] = sc.hadoopGeoTiffRDD(bandPath(2))
//    val greenRDD: RDD[(ProjectedExtent, Tile)] = sc.hadoopGeoTiffRDD(bandPath(3))
//    val redRDD: RDD[(ProjectedExtent, Tile)] = sc.hadoopGeoTiffRDD(bandPath(4))
//    val nirRDD: RDD[(ProjectedExtent, Tile)] = sc.hadoopGeoTiffRDD(bandPath(5))
//    val swir1RDD: RDD[(ProjectedExtent, Tile)] = sc.hadoopGeoTiffRDD(bandPath(6))
//    val swir2RDD: RDD[(ProjectedExtent, Tile)] = sc.hadoopGeoTiffRDD(bandPath(7))

    // Options设置切片策略
    try {
      val options = {
        HadoopGeoTiffRDD.Options(
          maxTileSize = Some(512),
          numPartitions = Some(100)
        )
      }


      type LandsatKey = (ProjectedExtent, URI, Int)

      /**
        * description: 高阶函数，uriToKey传入参数bandIndex，返回值为函数（uri,pe）=>(pe,uri,bandIndex)
        * created time:  2018/11/8
        *
        * params [bandIndex]
        *
        * @return (_root_.java.net.URI, _root_.geotrellis.vector.ProjectedExtent) => (_root_.geotrellis.vector.ProjectedExtent, _root_.java.net.URI, Int)
        */
      def uriToKey(bandIndex: Int): (URI, ProjectedExtent) => LandsatKey = { (uri, pe) =>
        (pe, uri, bandIndex)
      }

      // bandIndex 为波段号 -2
      // HadoopGeoTiffRDD 为自定义RDD[(LandsatKey, Tile)]
      // 另外的sc.hadoopGeoTiffRDD(bandPath(2))得到的是标准RDD[(ProjectedExtent, Tile)]
      val blueSourceTiles: RDD[(LandsatKey, Tile)] =
      HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
        bandPath(2), //uri
        uriToKey(0), //bandIndex
        options
      )
      val greenSourceTiles =
        HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
          bandPath(3),
          uriToKey(1),
          options
        )
      val redSourceTiles =
        HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
          bandPath(4),
          uriToKey(2),
          options
        )
      val nirSourceTiles =
        HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
          bandPath(5),
          uriToKey(3),
          options
        )
      val swir1SourceTiles =
        HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
          bandPath(6),
          uriToKey(4),
          options
        )
      val swir2SourceTiles: RDD[(LandsatKey, Tile)] =
        HadoopGeoTiffRDD[ProjectedExtent, LandsatKey, Tile](
          bandPath(7),
          uriToKey(5),
          options
        )

      //把这些单波段RDD合成为多波段RDD
      println("合成多波段rdd")
      val sourceTiles: RDD[(ProjectedExtent, MultibandTile)] = {
        sc.union(swir2SourceTiles, swir1SourceTiles, nirSourceTiles, redSourceTiles, greenSourceTiles, blueSourceTiles)
          .map { case ((pe, uri, bandIndex), tile) =>
            // Get the center of the tile, which we will join on
            val (x, y) = (pe.extent.center.x, pe.extent.center.y)

            // Round the center coordinates in case there's any floating point errors
            val center =
              (
                // setScale 设置小数点精度，RoundingMode.HALF_UP 四舍五入
                BigDecimal(x).setScale(5, RoundingMode.HALF_UP).doubleValue(),
                BigDecimal(y).setScale(5, RoundingMode.HALF_UP).doubleValue()
              )

            // Get the scene ID from the path
            val sceneId = uri.getPath.split('/').reverse.drop(1).head

            val newKey = (sceneId, center)
            val newValue = (pe, bandIndex, tile)
            (newKey, newValue)
          }
          .groupByKey()
          .map { case (oldKey, groupedValues) =>
            val projectedExtent = groupedValues.head._1
            //创建一个多维数组
            val bands = Array.ofDim[Tile](groupedValues.size)
            //将tile按照bandIndex放入到bands多维数组中
            for ((_, bandIndex, tile) <- groupedValues) {
              bands(bandIndex) = tile.convert(DoubleConstantNoDataCellType)
            }
            (projectedExtent, MultibandTile(bands))
          }
      }.cache()

      import geotrellis.raster.io.geotiff.GeoTiff
      import geotrellis.spark.tiling.FloatingLayoutScheme

      //得到元数据信息，sourceTiles: RDD[(ProjectedExtent, MultibandTile)]
      val (_, metadata) = sourceTiles.collectMetadata[SpatialKey](FloatingLayoutScheme(512))

      // Now we tile to an RDD with a SpatialKey.
      val tiles: RDD[(SpatialKey, MultibandTile)] = sourceTiles.tileToLayout[SpatialKey](metadata)

      //MultibandTileLayerRDD[SpatialKey] = RDD[(K, MultibandTile)] with Metadata[TileLayerMetadata[K]]

      val layerRdd: MultibandTileLayerRDD[SpatialKey] = ContextRDD(tiles, metadata).cache()

      import chd.raster.index.Indexes._

      println("开始计算FVC")
//      val fvcRdd = layerRdd.withContext { rdd =>
//        rdd.mapValues { tile =>
//          val nir = tile.band(3)
//          val red = tile.band(2)
//          getNdvi(red,nir)
//        }
//      }

      println("开始计算Ndvi")
      val ndviRdd = layerRdd.withContext { rdd =>
        rdd.mapValues { tile =>
          val nir = tile.band(3)
          val red = tile.band(2)
          getNdvi(red,nir)
        }
      }
      val rasterNDVI = ndviRdd.stitch()
      val sumDouble = rasterNDVI.tile.toArrayDouble().filter(x => isData(x))
      GeoTiff(rasterNDVI, metadata.crs).write("data\\output\\ndviSR.tif")

//
//      println("开始计算Albedo")
//      val albedoRdd = layerRdd.withContext { rdd =>
//        rdd.mapValues { tile =>
//          val swir2 = tile.band(5)
//          val swir1 = tile.band(4)
//          val nir = tile.band(3)
//          val red = tile.band(2)
//          val green = tile.band(1)
//          val blue = tile.band(0)
//          getAlbedo(blue,red,nir,swir1,swir2)
//        }
//      }
//
//
//      println("开始计算sumRdd")
//      val sumRdd = layerRdd.withContext { rdd =>
//        rdd.mapValues { tile =>
//          val swir2 = tile.band(5)
//          val swir1 = tile.band(4)
//          val nir = tile.band(3)
//          val red = tile.band(2)
//          val green = tile.band(1)
//          val blue = tile.band(0)
//          getSum(swir1, swir2, nir, red, green, blue)
//        }
//      }
//      val raster = sumRdd.stitch()
//      val sumDouble = raster.tile.toArrayDouble().filter(x => isData(x))
//      GeoTiff(raster, metadata.crs).write("data\\output\\sumSR.tif")
//
//      println("开始计算jenks")
//      val simpleArr = createRandomArr(sumDouble,10000)
//
//      val jenkArr = jenks(simpleArr, 6)
//      jenkArr.foreach(a => print(a.toString + "-"))

    } finally {
      sc.stop()
    }
  }
}
