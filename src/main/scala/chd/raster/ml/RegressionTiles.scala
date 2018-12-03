package chd.raster.ml

import astraea.spark.rasterframes._
import astraea.spark.rasterframes.ml.{NoDataFilter, TileExploder}
import geotrellis.raster._
import geotrellis.raster.render._
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.DecisionTreeClassifier
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.sql._
/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 10:00 2018/11/12
  * @ Description：直接对Tiles进行一元简单回归分析
  * @ Modified By：
  */
object RegressionTiles {
  def main(args: Array[String]): Unit = {
    implicit val spark = SparkSession.builder().
      master("local[*]").appName(getClass.getName).getOrCreate().withRasterFrames
    spark.sparkContext.setLogLevel("ERROR")

    import spark.implicits._

    // Utility for reading imagery from our test data set
    def readTiff(name: String): SinglebandGeoTiff = SinglebandGeoTiff(s"../core/src/test/resources/$name")

    val filenamePattern = "L8-%s-Elkton-VA.tiff"
    val bandNumbers = 2 to 7
    val bandColNames = bandNumbers.map(b ⇒ s"band_$b").toArray
    val tileSize = 10

    // For each identified band, load the associated image file
    val joinedRF = bandNumbers.
      map { b ⇒ (b, filenamePattern.format("B" + b)) }.
      map { case (b, f) ⇒ (b, readTiff(f)) }.
      map { case (b, t) ⇒ t.projectedRaster.toRF(tileSize, tileSize, s"band_$b") }.
      reduce(_ spatialJoin _)
  }
}
