package com.chinajey.dwork.modules.inventory.mapper;

import com.alibaba.fastjson.JSONObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Set;


/**
 * @author lst
 * @date 2023/12/16
 */
@Mapper
public interface InventoryMapper {


    /**
     * 查询位置下关联的所有物料信息
     */
    List<Object> findMaterialCodes(@Param("codes")List<Object> codes);

    /**
     * 查询仓库下关联的所有位置信息
     */
    List<Object> findLocationCodes(@Param("codes")List<Object> codes);

    /**
     * 查询区域下关联的所有仓库信息
     */
    List<Object> findWarehouseCodes(@Param("codes")List<Object> codes);

    /**
     * 查询位置(和物料)下的所有周转箱信息
     */
    Set<String> findPassBoxCodes(@Param("positionCodes")List<Object> positionCodes,@Param("materialCodes") List<Object> materialCodes);

    /**
     * 查询区域下的所有周转箱信息
     */
    List<Object> areaFindLocation(@Param("codes")List<Object> areaCodes);

    /**
     * 排除本次盘点单，根据周转箱编码查询未清、未下达的盘点单
     */
    JSONObject findInventoryCodes(@Param("codes")Set<String> codes,@Param("inventoryCode")String inventoryCode);

    /**
     * 根据位置编码查询仓库编码
     */
    String locationFindWarehouse(@Param("locationCode")String locationCode);


    /**
     * 查询初盘PDA任务状态
     */
    JSONObject findGN1414Task(@Param("inventoryAreaCode")String inventoryAreaCode,
                              @Param("inventoryWarehouseCode")String inventoryWarehouseCode,
                              @Param("inventoryPositionCode")String inventoryPositionCode,
                              @Param("inventoryMaterialCode")String inventoryMaterialCode,
                              @Param("inventorySheetCode")String inventorySheetCode);

    /**
     * 查询复盘PDA任务状态
     */
    JSONObject findGN3233Task(@Param("inventoryAreaCode")String inventoryAreaCode,
                              @Param("inventoryWarehouseCode")String inventoryWarehouseCode,
                              @Param("inventoryPositionCode")String inventoryPositionCode,
                              @Param("inventoryMaterialCode")String inventoryMaterialCode,
                              @Param("inventorySheetCode")String inventorySheetCode);


    /**
     * 根据周转箱实时编码 打开盘点锁定
     */
    void updateInventoryLocking(@Param("codes") List<String> codes, @Param("flag") boolean flag);


    /**
     * 查询盘点计划的 周转箱实时编码
     *
     */
    List<String> findInventorySheetPassBoxRealCode(@Param("id") Long id);
}
