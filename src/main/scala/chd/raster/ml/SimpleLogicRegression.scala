package chd.raster.ml

import java.io.File

import org.apache.spark.sql.Row
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.ml.feature.{HashingTF, IndexToString, StringIndexer, Tokenizer, VectorIndexer}
import org.apache.spark.ml.classification.LogisticRegression
import org.apache.spark.ml.classification.LogisticRegressionModel
import org.apache.spark.ml.classification.{BinaryLogisticRegressionSummary, LogisticRegression}
import org.apache.spark.sql.functions


/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 16:48 2018/11/8
  * @ Description：使用spark MLlib 对文本进行进行简单一元逻辑回归分析。
  * @ Modified By：
  */
case class Iris(features:Vector,label:String) //这个case class 一定要放在外面，
object SimpleLogicRegression {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder().master("local[*]")
      .appName("SimpleRegression")
      .getOrCreate()

    // 读取文本数据，使用“，”分割开，再把数据放入case类Iris转成DataFrame格式
    import spark.implicits._
    // val file = new File("src/resources/iris.txt")
    val data = spark.sparkContext
      .textFile("E:\\project\\Desertification-monitoring\\src\\main\\resources\\iris.txt")
      .map(_.split(","))
      .map(p => Iris(Vectors.dense(p(0).toDouble,p(1).toDouble,p(2).toDouble, p(3).toDouble), p(4).toString()))
      .toDF()

    data.show(20)

    data.createOrReplaceTempView("iris")
    val df = spark.sql("select * from iris where label != 'Iris-setosa'")
    df.map(t => t(1)+":"+t(0)).collect().foreach(println)

    val labelIndexer = new StringIndexer().setInputCol("label").setOutputCol("indexedLabel").fit(df)
    val featureIndexer = new VectorIndexer().setInputCol("features").setOutputCol("indexedFeatures").fit(df)
    val Array(trainingData, testData) = df.randomSplit(Array(0.7, 0.3))

    val lr = new LogisticRegression()
      .setLabelCol("indexedLabel")
      .setFeaturesCol("indexedFeatures")
      .setMaxIter(10)
      .setRegParam(0.3)
      .setElasticNetParam(0.8)

    val labelConverter = new IndexToString()
      .setInputCol("prediction")
      .setOutputCol("predictedLabel")
      .setLabels(labelIndexer.labels)

    val lrPipeline = new Pipeline().setStages(Array(labelIndexer, featureIndexer, lr, labelConverter))

    val lrPipelineModel = lrPipeline.fit(trainingData)

    val lrPredictions = lrPipelineModel.transform(testData)

    lrPredictions.select("predictedLabel", "label", "features", "probability")
      .collect()
      .foreach { case Row(predictedLabel: String, label: String, features: Vector, prob: Vector)
      => println(s"($label, $features) --> prob=$prob, predicted Label=$predictedLabel")}

    val evaluator = new MulticlassClassificationEvaluator()
      .setLabelCol("indexedLabel")
      .setPredictionCol("prediction")

    val lrAccuracy = evaluator.evaluate(lrPredictions)

    println("Test Error = " + (1.0 - lrAccuracy))

    val lrModel = lrPipelineModel.stages(2).asInstanceOf[LogisticRegressionModel]

    println("Coefficients: " +
      lrModel.coefficients+
      "Intercept: "+
      lrModel.intercept+"numClasses: " +
      lrModel.numClasses+"numFeatures: "+
      lrModel.numFeatures)

  }
}
