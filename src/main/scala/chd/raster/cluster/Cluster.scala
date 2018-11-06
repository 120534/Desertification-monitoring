package chd.raster.cluster
import astraea.spark.rasterframes._
import astraea.spark.rasterframes.ml.TileExploder
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.raster._
import geotrellis.raster.render._
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.clustering.{KMeans, KMeansModel}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.sql._


object Cluster extends App {
  // Utility for reading imagery from our test data set
  def readTiff(name: String): SinglebandGeoTiff = SinglebandGeoTiff(s"./work/$name")

  implicit val spark = SparkSession.builder().
    master("local[*]").appName(getClass.getName).getOrCreate().withRasterFrames
  spark.sparkContext.setLogLevel("ERROR")

  import spark.implicits._

  println("start reading geotiff,and converting to rasterframe...")
  val origin_tiff = SinglebandGeoTiff("D:\\GLDAS_test\\LC81220322015147LGN00\\L8-B4-Elkton-VA.tiff")
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

  val clusterColors = IndexedColorMap.fromColorMap(
    ColorRamps.Viridis.toColorMap((0 until k).toArray)
  )

  SinglebandGeoTiff(raster.tile,raster.extent,origin_tiff.crs).write("E:\\cluster_5_band.tif")
//  raster.tile.renderPng(clusterColors).write("E:\\clustered.png")

}
