package com.chinajey.dwork.modules.warehousingApplicant.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.tengnat.dwork.modules.script.service.SceneGroovyService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class WarehousingTaskService {

    @Resource
    private BmfService bmfService;

    @Resource
    private SceneGroovyService sceneGroovyService;

    @Resource
    private BusinessUtils businessUtils;

    /**
     * 取消入库任务
     */
    public void cancel(List<Long> ids) {
        if (ids == null) {
            throw new RuntimeException("参数缺失[ids]");
        }
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find("warehousingTask", id);
            if (bmfObject == null) {
                throw new BusinessException("入库任务不存在,id:" + id);
            }
            if (!DocumentStatusEnum.PENDING.getCode().equals(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "],不能取消");
            }
            // 更新转态
            BmfObject update = new BmfObject("warehousingTask");
            update.put("id", id);
            update.put("documentStatus", DocumentStatusEnum.CANCEL.getCode());
            this.bmfService.updateByPrimaryKeySelective(update);
            // 关闭业务
            this.businessUtils.closeCurrentAllTaskByDataSourceCode("GN10004", String.valueOf(bmfObject.getPrimaryKeyValue()));
            // 恢复入库确认任务
            List<BmfObject> details = bmfObject.getAndRefreshList("warehousingTaskDetailIdAutoMapping");
            for (BmfObject detail : details) {
                BmfObject warehousingApplicant = this.bmfService.findByUnique("warehousingApplicant", "code", detail.getString("warehousingApplicantCode"));
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("materialCode", detail.getString("materialCode"));
                jsonObject.put("materialName", detail.getString("materialName"));
                jsonObject.put("warehouseCode", detail.getString("targetWarehouseCode"));
                jsonObject.put("warehouseName", detail.getString("targetWarehouseName"));
                jsonObject.put("dataSourceType", "warehousingApplicantDetail");
                jsonObject.put("dataSourceCode", warehousingApplicant.getString("code"));
                jsonObject.put("preDocumentType", warehousingApplicant.getBmfClassName());
                jsonObject.put("preDocumentCode", warehousingApplicant.getString("code"));
                jsonObject.put("sourceDocumentType", warehousingApplicant.getString("sourceDocumentType"));
                jsonObject.put("sourceDocumentCode", warehousingApplicant.getString("sourceDocumentCode"));
                jsonObject.put("ext_code", warehousingApplicant.getString("code"));
                jsonObject.put("ext_plan_quantity", detail.getBigDecimal("planQuantity"));
                jsonObject.put("ext_order_business_type", warehousingApplicant.getString("orderBusinessType"));
                jsonObject.put("logisticsMoveBusinessType", warehousingApplicant.getString("orderBusinessType"));
                jsonObject.put("ext_line_nums", detail.getString("lineNum"));
                BmfObject unit = detail.getAndRefreshBmfObject("unit");
                jsonObject.put("quantityUnit", unit);
                if (unit != null) {
                    jsonObject.put("ext_unit_id", unit.getPrimaryKeyValue());
                    jsonObject.put("ext_unit", unit.getString("name"));
                }
                this.sceneGroovyService.buzSceneStart("GN10003", "BS10002", jsonObject);
            }
        }
    }
}
