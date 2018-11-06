import geotrellis.raster.DoubleConstantNoDataCellType
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import chd.raster.cluster.Classifier.jenks
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import chd.raster.index.Indexes._
/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 10:53 2018/11/6
  * @ Description：与ArcMap对比结果一致。
  * @ Modified By：
  */
object TestJenks {
  def main(args: Array[String]): Unit = {
    val path = "D:\\GLDAS_test\\sr"

    def bandPath(b: Int) = s"D:\\GLDAS_test\\sr\\LC08_L1TP_120032_20160920_20180205_01_T1_sr_band${b}.tif"

    println("start reading bands...")
    val blue_tif = GeoTiffReader.readSingleband(bandPath(2))
    val green_tif = GeoTiffReader.readSingleband(bandPath(3))
    val red_tif = GeoTiffReader.readSingleband(bandPath(4))

    val blue = blue_tif.tile.convert(DoubleConstantNoDataCellType)
    val green = green_tif.tile.convert(DoubleConstantNoDataCellType)
    val red = red_tif.tile.convert(DoubleConstantNoDataCellType)

    val sum = getSum(blue,green,red)

    SinglebandGeoTiff(sum,blue_tif.extent,blue_tif.crs).write("E://sum_rgb.tif")
  }
}
