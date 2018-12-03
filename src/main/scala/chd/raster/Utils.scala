package chd.raster

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 16:49 2018/11/28
  * @ Description：工具类
  * @ Modified By：
  */

object Utils {
  /**
    * description:根据数组的索引范围，计算随机索引，保证所取的索引无重复，进而实现对数组进行随机抽取。耗时
    * created time:  2018/11/27
    *
    *  params [arr, n]
    * @return ArrayBuffer[Double]
    */
  def createRandomArray(arr:Array[Double],n:Int): Array[Double] ={
    var outList:List[Int] = Nil
    var border = arr.length  //随机数的范围
    //    var arrIndex = Range(0, border-1).toArray

    var arrIndex = new Array[Int](border)
    for (i <- arrIndex.indices){
      arrIndex(i) = i
    }

    //这个循环是为了提取随机数在原数组中的索引位置。
    for (i <- 0 until n){
      val index = (new Random).nextInt(border) //限制随机数范围，这个随机数是指arrIndex角标范围
      outList = outList:::List(arrIndex(index))  //放入的是需要取出的数组角标
      arrIndex(index) = arrIndex.last     //移除已经放入的角标
      arrIndex = arrIndex.dropRight(1)//去除已经放入角标的数组元素。
      border-=1       //arrIndex的size减了1，对应border也要减1
    }

    val it = outList.toIterator
    val arrayBuffer = new ArrayBuffer[Double]()
    while(it.hasNext){
      arrayBuffer += arr(it.next())
    }
    arrayBuffer.toArray
  }

  /**
    * description: 不考虑索引重复的问题，就是说有些原数组的值可以被多次随机取中。比createRandomArray快很多
    * created time:  2018/11/28
    *
    *  params [arr, n]
    * @return _root_.scala.Array[Double]
    */
  def createRandomArr(arr:Array[Double],n:Int):Array[Double] = {
    val length = arr.length
    val arrDouble = new Array[Double](n)
    //不考虑index可能出现重复的情况
    for (i <- 0 until n) {
      //随机取索引，得到索引值，然后从原数组中提取出来，再赋值给新数组。
      val randomIndexes = Random.nextInt(length)
      arrDouble(i) = arr(randomIndexes)
    }
    arrDouble
  }
  //经过多次实验，使用全部数据进行自然间断得到【1.0-1666.0-3332.0-4998.0-6665.0-8332.0-9999.0】
  // 使用随机抽取数据做间断得到【1.0-1666.0-3332.0-4998.0-6665.0-8332.0-9999.0】
  //再尝试一下创建一个随机数组，用随机数组进行实验（因为之前实验的数据为等距的）
  //全部数据计算自然间断点 0.20813 - 1622.5584 - 3281.69885 - 4975.46215 - 6666.49871 - 8348.45788 - 9998.77669
  //抽取其中1000个数组计算 1.30809 - 1587.32722 - 3133.93872 - 4811.67223 - 6539.922 - 8315.81403 - 9994.74727
}
