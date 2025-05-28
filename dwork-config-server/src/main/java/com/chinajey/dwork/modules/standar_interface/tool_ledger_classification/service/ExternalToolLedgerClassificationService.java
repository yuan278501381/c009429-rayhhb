package com.chinajey.dwork.modules.standar_interface.tool_ledger_classification.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.CodeAssUtils;
import com.chinajey.dwork.modules.standar_interface.tool_ledger_classification.domain.dto.ExternalToolLedgerClassificationDto;
import com.tengnat.dwork.common.utils.CodeGenerator;
import com.tengnat.dwork.common.utils.TreeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class ExternalToolLedgerClassificationService {

    @Resource
    private BmfService bmfService;

    @Resource
    private CodeGenerator codeGenerator;

    public void saveOrUpdate(ExternalToolLedgerClassificationDto classificationDto) {
        String externalCode = classificationDto.getCode();
        BmfObject classification = this.bmfService.findByUnique(classificationDto.getBmfClassName(), "externalDocumentCode", externalCode);
        if (classification != null) {
            this.updateToolLedgerClassification(classificationDto, classification);
        } else {
            this.saveToolLedgerClassification(classificationDto);
        }
    }

    public void saveToolLedgerClassification(ExternalToolLedgerClassificationDto classificationDto) {
        BmfObject classificationBmfObj = BmfUtils.genericFromJsonExt(JSONObject.parseObject(JSONObject.toJSONString(classificationDto)), classificationDto.getBmfClassName());
        classificationBmfObj.put("externalDocumentCode", classificationDto.getCode());
        CodeAssUtils.setCode(classificationBmfObj, classificationDto.getCode());
        this.check(classificationBmfObj);
        this.bmfService.saveOrUpdate(classificationBmfObj);
    }

    public void updateToolLedgerClassification(ExternalToolLedgerClassificationDto classificationDto, BmfObject classification) {
        BmfObject classificationBmfObj = BmfUtils.genericFromJsonExt(JSONObject.parseObject(JSONObject.toJSONString(classificationDto)), classificationDto.getBmfClassName());
        classification.putAll(classificationBmfObj);
        this.check(classification);
        this.bmfService.saveOrUpdate(classification);
    }

    private void check(BmfObject classificationBmfObj) {
        this.handleParent(classificationBmfObj);
        this.checkUnique(classificationBmfObj);
    }

    private void handleParent(BmfObject classificationBmfObj) {
        String parentExternalCode = classificationBmfObj.getString("parentExternalCode");
        if (StringUtils.isNotBlank(parentExternalCode)) {
            BmfObject parent = this.bmfService.findByUnique(classificationBmfObj.getBmfClassName(), "externalCode", parentExternalCode);
            classificationBmfObj.put("parent", parent);
        }
    }

    private void checkUnique(BmfObject classificationBmfObj) {
        String bmfClassName = classificationBmfObj.getBmfClassName();
        String descriptionName = classificationBmfObj.getString("descriptionName");
        Long classificationDtoId = classificationBmfObj.getLong("id");
        String classificationCode = classificationBmfObj.getString("code");
        String name = classificationBmfObj.getString("name");
        if (classificationDtoId != null) {
            BmfObject knifeClassification = this.bmfService.find(bmfClassName, classificationDtoId);
            if (knifeClassification == null) {
                throw new BusinessException(descriptionName + "类别不存在,id:" + classificationDtoId);
            }

        }

        BmfObject bmfObject = this.bmfService.findByUnique(bmfClassName, "code", classificationCode);
        if (bmfObject != null && !bmfObject.getPrimaryKeyValue().equals(classificationDtoId)) {
            throw new BusinessException(descriptionName + "类别编码不能重复");
        }
        Long parentId = null;
        BmfObject parent = classificationBmfObj.getBmfObject("parent");
        if (parent != null) {
            parentId = parent.getLong("id");
        }

        TreeUtils.checkCategorySameName(bmfClassName, parentId, name, classificationDtoId);
        TreeUtils.checkParent(bmfClassName, classificationDtoId, parentId);
    }


}
