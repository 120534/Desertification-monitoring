package chd.raster.ml

import org.apache.spark.sql.SparkSession
import chd.raster.ml.Classifier._
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature.{StringIndexer, VectorIndexer}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.regression.LinearRegression
/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 17:28 2018/11/11
  * @ Description：一元线性回归 使用DataFrame api，以及spark MLlib库
  * @ Modified By：
  */
case class Reg(features:Vector,label:Double)
object SimpleLinearRegression {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[*]")
      .appName("SimpleRegression")
      .getOrCreate()

    val arr = new Array[Int](1000)
    for (i:Int <- 0 until arr.length-1){
      arr(i) = i
    }

    val features = arr.map(_.toDouble)

    val confer = new Array[Int](1000)
    for (i:Int <- 0 until confer.length-1){
      confer(i) = i*(-5)+4
    }

    val label = confer.map(_.toDouble)

    val c = features.zip(label)

    // 读取文本数据，使用“，”分割开，再把数据放入case类Iris转成DataFrame格式
    import spark.implicits._
    val dataRDD = spark.sparkContext.parallelize(c)
    val data = dataRDD.map(a => Reg(Vectors.dense(a._1),a._2)).toDF()

//    val labelIndexer = new StringIndexer().setInputCol("label").setOutputCol("indexedLabel").fit(df)

    data.show(20)
    val linearReg = new LinearRegression()
      .setMaxIter(10)
      .setRegParam(0.3)
      .setElasticNetParam(0.8)

    val lrModel= linearReg.fit(data)

    println(s"Coefficients: ${lrModel.coefficients} Intercept: ${lrModel.intercept}")

    val trainingSummary = lrModel.summary
    println(s"numIterations: ${trainingSummary.totalIterations}")
    println(s"objectiveHistory: [${trainingSummary.objectiveHistory.mkString(",")}]")
    trainingSummary.residuals.show()
    println(s"RMSE: ${trainingSummary.rootMeanSquaredError}")
    println(s"r2: ${trainingSummary.r2}")


  }
}
