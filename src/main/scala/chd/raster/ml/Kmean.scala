package chd.raster.ml

import astraea.spark.rasterframes._
import astraea.spark.rasterframes.ml.TileExploder
import geotrellis.raster._
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.raster.render._
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.clustering.{KMeans, KMeansModel}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql._


object Kmean extends App {
  // Utility for reading imagery from our test data set
  def readTiff(name: String): SinglebandGeoTiff = SinglebandGeoTiff(s"./work/$name")

  implicit val spark = SparkSession.builder().
    master("local[*]").appName(getClass.getName).getOrCreate().withRasterFrames
  spark.sparkContext.setLogLevel("ERROR")

  import spark.implicits._

  println("start reading geotiff,and converting to rasterframe...")
  val origin_tiff = SinglebandGeoTiff("D:\\GLDAS_test\\LC81220322015147LGN00\\L8-B4-Elkton-VA.tiff")
  // 1、先读取GeoTiff，再转换为RasterFrame数据类型
  val joinedRF = origin_tiff.projectedRaster.toRF("band2")

  // 直接使用sparksession.read.geotiff.loadRF(path)来读取tiff数据为RasterFrame格式
  // val samplePath = new File("./work/LC08_L1TP_120032_20160920_20180205_01_T1_sr_band2.tif")
  // val tiffRF = spark.read.geotiff.loadRF(samplePath.toURI)

  // For each identified band, load the associated image file, convert to a RasterFrame, and join
  //官网给出的代码里面，
//  val joinedRF = bandNumbers. //bandNumbers = 1 to 4
//    map { b ⇒ (b, filenamePattern.format(b)) }.
//    map { case (b, f) ⇒ (b, readTiff(f)) }.
//    map { case (b, t) ⇒ t.projectedRaster.toRF(s"band_$b") }.
//    reduce(_ spatialJoin _)

  //SparkML requires that each observation be in its own row, and those observations be packed into a single Vector.
  // The first step is to “explode” the tiles into a single row per cell/pixel.
  //spark要求每个观测值占一行，这些观测值被打包成一个独立的向量。
  //第一步就是要“分离”这些瓦片，把它们转换成每个像元一个单独行。
  val exploder = new TileExploder()
  //To “vectorize” the the band columns, as required by SparkML, we use the SparkML VectorAssembler.
  // We then configure our algorithm,create the transformation pipeline, and train our model.
  // (Note: the selected value of K below is arbitrary.)
  //SparkML需要对这些波段的列进行“向量化”，使用到了VectorAssember，然后配置了算法，创建转换pipeline，训练模型。
  val assembler = new VectorAssembler().
    setInputCols(Array[String]("band2")). //这里参数是Array(bandColNames)
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

  val clusterColors = IndexedColorMap.fromColorMap(
    ColorRamps.Viridis.toColorMap((0 until k).toArray)
  )

  SinglebandGeoTiff(raster.tile,raster.extent,origin_tiff.crs).write("E:\\cluster_5_band.tif")
//  raster.tile.renderPng(clusterColors).write("E:\\clustered.png")

}
