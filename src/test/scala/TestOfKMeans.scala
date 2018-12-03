import astraea.spark.rasterframes._
import astraea.spark.rasterframes.ml.TileExploder
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.vectorize.Vectorize
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.clustering.{KMeans, KMeansModel}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql._

object TestOfKMeans {
  def main(args: Array[String]): Unit = {
    implicit val spark = SparkSession.builder().
      master("local[*]").appName(getClass.getName).getOrCreate().withRasterFrames
    spark.sparkContext.setLogLevel("ERROR")

    import spark.implicits._

    println("start reading geotiff,and converting to rasterframe...")

    //获取原始影像和瓦片
    val origin_tiff = SinglebandGeoTiff("D:\\GLDAS_test\\LC81220322015147LGN00\\L8-B4-Elkton-VA.tiff")
    val origin_tile = origin_tiff.tile.convert(DoubleConstantNoDataCellType)

    val joinedRF = origin_tiff.projectedRaster.toRF("band2")

    // val samplePath = new File("./work/LC08_L1TP_120032_20160920_20180205_01_T1_sr_band2.tif")
    // val tiffRF = spark.read.geotiff.loadRF(samplePath.toURI)

    val exploder = new TileExploder()

    val assembler = new VectorAssembler().
      setInputCols(Array[String]("band2")).
      setOutputCol("features")

    // Configure our clustering algorithm
    val k = 6
    val kmeans = new KMeans().setK(k)

    // Combine the two stages
    println("create pipeline...")
    val pipeline = new Pipeline().setStages(Array(exploder, assembler, kmeans))

    // Compute clusters
    println("create model")
    val model = pipeline.fit(joinedRF)

    println("create model and make transform")
    val clustered = model.transform(joinedRF)
    //这个clustered对象，已经包含了我们需要的分类信息，只是还没聚集在一起。
    println(clustered.count())
    clustered.show(8)
    clustered.select(($"prediction")).filter(_ == 0)
    joinedRF.select()

    val clusterResults = model.stages.collect{ case km: KMeansModel ⇒ km}.head
    val metric = clusterResults.computeCost(clustered)

    println("Within set sum of squared errors: " + metric)

    println("retiling...")
    val tlm = joinedRF.tileLayerMetadata.left.get
    val retiled = clustered.groupBy($"spatial_key").agg(
      assembleTile(
        $"column_index", $"row_index", $"prediction",
        tlm.tileCols, tlm.tileRows, ByteConstantNoDataCellType
      )
    )
    retiled.printSchema()
    retiled.show(8)

    println("turning Dataframe to rasterframe...")
    val rf = retiled.asRF($"spatial_key", tlm)
    rf.show(8)
    val raster = rf.toRaster($"prediction", 186, 169)

    val tile_clustered = raster.tile

//    val multipolygon = Vectorize(raster.tile,raster.extent)

    //得到沙地的矢量数据
//    val area_of_sand = multipolygon.filter(i => i.data == 0)
    //进行mask
/*
  这里直接可以使用georellis强大的瓦片计算，通过map algebra来把分类后的沙地移除掉。
  同时可以考虑是否可以先计算指数，最后再移除不需要计算沙漠化的水域和沙地...不过要拟合的话，
  暂时还是得去除这些土地类型以及噪点数据。
  具体操作如下。
 */

    val tile = tile_clustered.combineDouble(origin_tile)(
      (tile1,tile2) => if (tile1.toInt != 0) tile2 else Double.NaN
    )
      SinglebandGeoTiff(tile,origin_tiff.extent,origin_tiff.crs).write("E:\\No_sand.tif")
//    tile.renderPng(ColorMap.fromQuantileBreaks(tile.histogramDouble,ColorRamps.BlueToOrange))
//      .write("E:\\Mix3.png")
    //    val clusterColors = IndexedColorMap.fromColorMap(
//      ColorRamps.Viridis.toColorMap((0 until k).toArray)
//    )
//
//    raster.tile.renderPng(clusterColors).write("E:\\clustered.png")


  }
}
