import java.awt.event.{ActionEvent, ActionListener}

import javax.swing.JButton

import scala.collection.mutable.ArrayBuffer


object Functions {
  def main(args: Array[String]): Unit = {

//    val arrbuffer = ArrayBuffer[ArrayBuffer[Double]]()
//
//    for (x <- 1 until 5) {
//      var temp = ArrayBuffer[Double]()
//      for (j <- 1 until 5 + 1) {
//        temp += 5
//      }
//      arrbuffer += temp
//    }
//
//    arrbuffer.foreach(_.foreach(a =>print(a.toString+",")))

    //    val sayHello = (name:String) => println(name)
    //      //匿名函数 (参数别名:类型 => 方法体)
    //    def greeting(func:String => Unit,string: String ): Unit ={
    //      // 传入匿名函数的规则  （函数别名:函数参数类型 => 返回值类型）
    //      func(string)
    //    }
    //    greeting(sayHello,"wanghl")
    //
    //    Array(1,2,3,4,5).map((num:Int)=>num*num)
    //
    //    def regard(msg:String) = (name:String) =>println(msg + name)
    //      //返回函数时，(函数参数类型 => 函数返回值类型)。函数体类与其他定义匿名函数一致。
    //    val message = regard("Hi,")
    //    message("John Lennon")
    //    val mobilePhone = (name:String) => println(name)
    //    def getName(func:String => Unit , str:String) = {
    //      func(str)
    //    }
    //
    //    def getFunc(func:String => Unit, name:String):String=>Unit={
    //      func(name)
    //      name:String => println(name)
    //    }
    //    val a = (1 to 20) reduceLeft(_+_)
    //    println(a)
    //    val arr = Array(3,4,5,61,1,2,3,56,8,10).sorted
    //    arr.foreach((a:Int)=>print("arr:"+ a.toString + ","))
    //    val arr2 = Array(3,4,5,61,1,2,3,56,8,10).sortWith(_>_)
    //    arr2.foreach(println(_))
    //闭包，函数在变量不处于其有效作用域时，还能够对变量进行访问。
    //    def greeting(name: String) = {
    //      def sayHello(name: String): String = {
    //        "hello," + name
    //      }
    //      sayHello("wanghl")
    //    }
//    implicit def convert(func: (ActionEvent) => Unit) = {
//      new ActionListener {
//        override def actionPerformed(e: ActionEvent): Unit = func(e)
//      }
//    }
//
//    val button = new JButton("Click")
//    button.addActionListener(
//      (e: ActionEvent) => println(e)
//    )

    //  val button = new JButton("Click")
    //
    //  implicit def getActionListener(actionProcessFunc: (ActionEvent) => Unit) = new ActionListener {
    //    override def actionPerformed(event: ActionEvent) {
    //      actionProcessFunc(event)
    //    }
    //  }
    //  button.addActionListener((event: ActionEvent) => println("Click Me!!!"))

  }
}