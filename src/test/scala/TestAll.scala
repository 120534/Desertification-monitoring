import com.typesafe.config.ConfigFactory
import geotrellis.raster.DoubleConstantNoDataCellType
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.raster.render.ColorMap
import chd.raster.cluster.Classifier._
import chd.raster.index.Indexes._

/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 11:14 2018/11/6
  * @ Description：None
  * @ Modified By：
  */
object TestAll {
  def main(args: Array[String]): Unit = {
    val path = "D:\\GLDAS_test\\sr"

    def bandPath(b: Int) = s"D:\\GLDAS_test\\sr\\LC08_L1TP_120032_20160920_20180205_01_T1_sr_band${b}.tif"

    println("start reading bands...")
    val blue = GeoTiffReader.readSingleband(bandPath(2)).tile.convert(DoubleConstantNoDataCellType)
    val green = GeoTiffReader.readSingleband(bandPath(3)).tile.convert(DoubleConstantNoDataCellType)
    val red = GeoTiffReader.readSingleband(bandPath(4)).tile.convert(DoubleConstantNoDataCellType)
    val nir = GeoTiffReader.readSingleband(bandPath(5)).tile.convert(DoubleConstantNoDataCellType)
    val swir1 = GeoTiffReader.readSingleband(bandPath(6)).tile.convert(DoubleConstantNoDataCellType)
    val swir2 = GeoTiffReader.readSingleband(bandPath(7)).tile.convert(DoubleConstantNoDataCellType)

    println("extracting tiles...")
//    val blue = geotiff_2.tile.convert(DoubleConstantNoDataCellType)
//    val red = geotiff_4.tile.convert(DoubleConstantNoDataCellType)
//    val nir = geotiff_5.tile.convert(DoubleConstantNoDataCellType)
//    val swir1 = geotiff_6.tile.convert(DoubleConstantNoDataCellType)
//    val swir2 = geotiff_7.tile.convert(DoubleConstantNoDataCellType)
//    val (blue, red, nir, swir1, swir2) = (geotiff_2.tile.convert(DoubleConstantNoDataCellType),
//      geotiff_4.tile.convert(DoubleConstantNoDataCellType),
//      geotiff_5.tile.convert(DoubleConstantNoDataCellType),
//      geotiff_6.tile.convert(DoubleConstantNoDataCellType),
//      geotiff_7.tile.convert(DoubleConstantNoDataCellType))

    //获取几个指数，这里后期可以考虑一下QA波段去云。
    val bandSum = getSum(blue, red, nir, swir1, swir2)
//    val albedo = getAlbedo(blue, red, nir, swir1, swir2)
//    val ndvi = getNdvi(red,nir)

    //归一化
    println("对albedo进行归一化计算")
//    val albedoNor = normalize(albedo)

    //对band_sum进行重分类，去除沙地信息，使用Jenks natural breaks,把地表反照率最大的一类（沙地），提出取来。
    val bandSumArr = bandSum.toArrayDouble()
    val classifiedArr = jenks(bandSumArr,6)
    println("自然间断点计算，得到反照率最大的类别，沙地。")
    classifiedArr.foreach(a => print(a.toString+"-"))

    /*
  提取ndvi瓦片
   */
    //      val rows = geotiff_2.rows
    //      val cols = geotiff_2.cols
    //      val ndvi = ndvi_tile(geotiff_4.tile,geotiff_5.tile)
    //      val colorMap = ColorMap.fromStringDouble(ConfigFactory.load().getString("ndviColormap")).get
    //      c

    /*
  提取地表反照率瓦片  Albedo=0.356BLUE+0.13RED+0.373NIR+0.085SWIR1+0.072SWIR2-0.0018
   */
    //    println("calculate albedo ...")
    //    val albedo = albedo_tile(blue, red, nir, swir1, swir2)
    //    albedo.renderPng(ColorRamps.HeatmapBlueToYellowToRedSpectrum).write("E://albedo.png")
  }
}
