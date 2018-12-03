package chd.raster.ml

import scala.collection.mutable.ArrayBuffer

/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 10:34 2018/11/6
  * @ Description：Classifier对象包含了一些分类方法，主要是聚类算法和非监督分类
  * @ Modified By：
  */
object Classifier {

    /**
     * description:jenks natural breaks 自然间断点法  【后期需要对参数修改为泛型类型参数。】
     * created time:  2018/11/6
     *
     *  params [arr, nbClass]
     * @return _root_.scala.collection.mutable.ArrayBuffer[Double]
     */
  def jenks(arr:Array[Double],nbClass:Int) = {

    if (arr.length == 0) throw new Exception("传入数组为空")
    if (nbClass <= 1) throw new Exception("分类数必须为大于1的正数")
    //传入的数组需要进行排序
    val dataList = arr.sorted
    val mat1 = ArrayBuffer[ArrayBuffer[Double]]()
    for (x <- 0 until dataList.length + 1) {
      var temp = ArrayBuffer[Double]()
      for (j <- 0 until nbClass + 1) {
        temp += 0
      }
      mat1 += temp
    }

    var mat2 = ArrayBuffer[ArrayBuffer[Double]]()
    for (i <- 0 until dataList.length + 1) {
      var temp = ArrayBuffer[Double]()
      for (j <- 0 until nbClass + 1) {
        temp += 0
      }
      mat2 += temp
    }


    for (y <- 1 until nbClass + 1) {
      mat1(0)(y) = 1.0
      mat2(0)(y) = 0.0
      for (t <- 1 until dataList.length + 1) {
        mat2(t)(y) = Double.PositiveInfinity //不知道这里的无穷大是否对等JS中的Infinity
      }
    }
    var v = 0.0

    for ( l <-  2 until dataList.length + 1) {
      var s1 = 0.0
      var s2 = 0.0
      var w = 0.0
      for ( m <- 1 until l + 1) {
        var i3 = l - m + 1
        var va = dataList(i3 - 1)

        s2 += va * va
        s1 += va
        w += 1
        v = s2 - (s1 * s1) / w
        var i4 = i3 - 1
        if (i4 != 0) {
          for ( p <- 2 until nbClass + 1) {
            if (mat2(l)(p) >= (v + mat2(i4)(p - 1))) {
              mat1(l)(p) = i3
              mat2(l)(p) = v + mat2(i4)(p - 1)
            }
          }
        }
      }
      mat1(l)(1) = 1
      mat2(l)(1) = v
    }
    var k = dataList.length
    val kclass = ArrayBuffer[Double]()


    for (i <- 0 to nbClass) {
      kclass += 0
    }

    kclass(nbClass) = dataList(dataList.length - 1)

    kclass(0) = dataList(0)
    var countNum = nbClass

    while (countNum >= 2) {
      val id = mat1(k)(countNum) - 2
      kclass(countNum - 1) = dataList(id.toInt)
      k = (mat1(k)(countNum) - 1).toInt

      countNum -= 1
    }

    if (kclass(0) == kclass(1)) {
      kclass(0) = 0
    }
    val bounds = kclass
    kclass
  }

}
