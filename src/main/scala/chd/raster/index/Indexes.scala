package chd.raster.index

import geotrellis.raster.mapalgebra.local.{Abs, Sqrt}
import geotrellis.raster.{DoubleConstantNoDataCellType, Tile, isData}
import geotrellis.raster.mapalgebra.local._
import geotrellis.raster.{DoubleArrayTile, DoubleConstantNoDataCellType, IntArrayTile, IntConstantNoDataArrayTile, MacroGeotiffMultibandCombiners, Tile, isData}
import geotrellis.raster.io.geotiff.SinglebandGeoTiff
import geotrellis.raster.io.geotiff.reader.GeoTiffReader
import geotrellis.spark.io.hadoop.HadoopGeoTiffRDD
import org.geotools.filter.function.JenksNaturalBreaksFunction
import geotrellis.raster._

/**
  * @ Author     ：wanghl
  * @ Date       ：Created in 16:31 2018/11/5
  * @ Description：Indexes对象包含了一系列指数模型的调用方法，参数大都为Tile，返回值也是Tile。CellType需要转为Double类型
  *                 注意使用mapalgebra方法时候，顺序不能颠倒，Tile * 3 不能写成 3 * Tile，因为*方法是属于Tile类的。
  * @ Modified By：
  */
object Indexes {

    /**
     * description:
     * created time:  2018/11/5
     *
     *  params [red, nir]
     * @return _root_.geotrellis.raster.Tile
     */
  def getNdvi(red: Tile, nir: Tile): Tile = {
    if (!(red.cellType == DoubleConstantNoDataCellType) && (nir.cellType == DoubleConstantNoDataCellType)){
      throw new Exception("wrong cellType")
    }
    red.combineDouble(nir) { (r: Double, ir: Double) => {
      if (isData(r) && isData(ir)) {
        (ir - r) / (ir + r)
      } else {
        Double.NaN
      }
    }
    }
  }

    /**
     * description: 传入可变参数瓦片，对其进行像元值累加。
     * created time:  2018/11/5
     *
     *  params [tile]
     * @return _root_.geotrellis.raster.Tile
     */
  def getSum(tile:Tile*):Tile = {
    tile.reduce(_+_)
  }

    /**
     * description: 对得到的指数进行归一化处理，Geotrellis也提供了 geotrellis.raster.Tile.normalize方法
     * created time:  2018/11/5
     *  内存溢出报错，需要进行重写。
     *  params [tile]
     * @return _root_.geotrellis.raster.Tile
     */
  def normalize (tile:Tile) : Tile = {
    val (min,max) = tile.findMinMaxDouble
    if (tile.cellType == DoubleConstantNoDataCellType){
      (tile - min)/(max - min)
    }else{
      tile.convert(DoubleConstantNoDataCellType)
      (tile - min)/(max - min)
    }
  }

    /**
     * description: 计算地表反照率，传入数据为地表反射率。调用了geotrellis提供的map algebra方法
     * created time:  2018/11/5
     *
     *  params [blue, red, nir, swir1, swir2]
     * @return _root_.geotrellis.raster.Tile
     */
  def getAlbedo(blue: Tile, red: Tile, nir: Tile, swir1: Tile, swir2: Tile):Tile = {
    blue * 0.356 + red * 0.13 + nir * 0.373 + swir1 * 0.085 + swir2 * 0.072 - 0.0018
  }

    /**
     * description:
     * created time:  2018/11/5
     *
     *  params [red, nir]
     * @return _root_.geotrellis.raster.Tile
     */
  def getMSAVI(red:Tile,nir:Tile):Tile = {
    (nir * 2 + 1 - Abs(Sqrt((nir * 2 + 1) * (nir * 2 + 1) - (nir - red) * 8))) / 2
  //  （2*float(b5)+1-abs(sqrt((2*float(b5)+1)*(2*float(b5)+1)-8*(float(b5)-float(b4))))/2
  }

    /**
     * description:
     * created time:  2018/11/5
     *
     *  params [blue, green, red]
     * @return Unit
     */
  def getTGSI(blue:Tile,green:Tile,red:Tile): Unit ={
    (red - blue)/(red + blue + green)
  }

/*
  * 计算FVC有两种方法：
  *   第一种是当区域内可以近似取VFCmax=100%，VFCmin=0%。
  * VFC = (NDVI - NDVImin)/ ( NDVImax - NDVImin)
  * NDVImax 和NDVImin分别为区域内最大和最小的NDVI值。由于不可避免存在噪声，
  * NDVImax 和NDVImin一般取一定置信度范围内的最大值与最小值，置信度的取值主要根据图像实际情况来定。
  *
  *   第二种是当区域内不能近似取VFCmax=100%，VFCmin=0%
  *有实测数据的情况下，取实测数据中的植被覆盖度的最大值和最小值作为VFCmax和 VFCmin，
  * 这两个实测数据对应图像的NDVI作为NDVImax 和NDVImin。
  *当没有实测数据的情况下，取一定置信度范围内的NDVImax 和NDVImin。VFCmax和 VFCmin根据经验估算。
  *
 */

  /**
    * description:第一种方法
    * created time:  2018/11/30
    *
    *  params [ndvi]
    * @return _root_.geotrellis.raster.Tile
    */
  def getFVC(ndvi:Tile):Tile={
    val quantile = ndvi.histogram.quantileBreaks(20)
    val ndviSoil = quantile(1)
    val ndviVeg = quantile(quantile.length - 2)

    println("ndviSoil: " + ndviSoil + ", ndviVeg: "+ ndviVeg)
    getFVC(ndvi, ndviSoil, ndviVeg)

  }

/**
 * description:第二种方法
 * created time:  2018/11/30
 *
 *  params [ndvi, ndviSoil, ndviVeg]
 * @return _root_.geotrellis.raster.Tile
 */
  def getFVC(ndvi:Tile,ndviSoil:Double,ndviVeg:Double): Tile ={
    (ndvi - ndviSoil) / (ndviVeg - ndviSoil)
    //FVC = (NDVI-NDVIsoil)/(NDVIveg-NDVIsoil)
  }

}

