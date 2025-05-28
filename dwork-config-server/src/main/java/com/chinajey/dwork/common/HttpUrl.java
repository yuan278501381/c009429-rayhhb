package com.chinajey.dwork.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author ChenYang
 * date 2023-02-13
 */
@Getter
@AllArgsConstructor
public class HttpUrl {

    /**
     * 通用接口 /open-api/bmf-class/{bmfClass}?access_token=ACCESS_TOKEN
     * 查询 GET
     * 新增 POST
     * 修改 PUT
     * 删除 DELETE
     *
     */
    public static final String API = "/open-api/bmf-class";
    /**
     * 新增不走编码规则   /open-api/bmf-class/create/{bmfClass}
     */
    public static final String CREATE = "/open-api/bmf-class/create";

    /**
     * 部分更新
     */
    public static final String PARTIALUPDATE= "/open-api/bmf-class/update/selective";

    /**
     * 物流箱单Api
     */
    public static final  String LOGISTICS = "/open-api/logistics-order";

    /**
     * 下达API
     */
    public static final String ASSIGN ="/open-api/logistics/assign";

    /**
     * 手动下达API
     */
    public static final String ASSIGN_HAND ="/open-api/logistics/assignHand";

    /**
     * 回调数据接口
     */
    public static final String CHECK_FINISH ="/open-api/logistics/checkFinish";

    /**
     * 下单拣配单
     */
    public static final String ASSIGN_PICKING= "/open-api/logistics/assign/pickingTask";


    /**
     * 关闭业务
     */
    public static final String BUZ_SCENE_CLOSE= "/open-api/buz-scene/close";
    
    
    public static final String INFO= "/auth/info";

    /**
     * 同步周转箱实时信息
     */
    public static final String SYNCHRONIZE_PASS_BOX_INFO= "/open-api/passBox/synchronizePassBoxInfo";

    /**
     * 周转箱装箱
     */
    public static final String PASS_BOX_PACKING= "/open-api/passBox/packing";
    /**
     * 批量提交
     */
    public static final String LOGISTICS_BATCH_HANDLE= "/open-api/logistics/batch/handle";

    /**
     * 创建异常物料
     */
    public static final String CREATE_ABNORMAL_MATERIAL= "/open-api/oa/createAbnormalMaterial";

    /**
     *下达业务
     */
    public static final String ASSIGN_LOGISTICS= "/open-api/logistics/batch/handle";




}
