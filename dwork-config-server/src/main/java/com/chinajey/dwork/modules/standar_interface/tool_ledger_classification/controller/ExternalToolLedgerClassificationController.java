package com.chinajey.dwork.modules.standar_interface.tool_ledger_classification.controller;

import com.chinajey.application.common.annotation.BusinessType;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.standar_interface.tool_ledger_classification.domain.dto.ExternalToolLedgerClassificationDto;
import com.chinajey.dwork.modules.standar_interface.tool_ledger_classification.service.ExternalToolLedgerClassificationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/external/sync-toolLedger-classification")
public class ExternalToolLedgerClassificationController {

    @Resource
    private ExternalToolLedgerClassificationService toolLedgerClassificationService;

    private static final Map<String, String > classificationMap = new HashMap<>();

    static {
        classificationMap.put("knifeClassification", "刀具类别");
        classificationMap.put("moldClassification", "模具类别");
        classificationMap.put("fixtureClassification", "夹具类别");
        classificationMap.put("jigClassification", "治具类别");
        classificationMap.put("rackClassification", "挂具类别");
        classificationMap.put("measuringToolClassification", "量检具类别");
        classificationMap.put("sparePartsClassification", "备品备件类别");
    }
    @PostMapping("/{bmfClassName}")
    @BusinessType("台账类别同步接口")
    public InvokeResult syncLedgerClassification(@PathVariable String bmfClassName, @RequestBody ExternalToolLedgerClassificationDto classificationDto) {
        String classificationName = bmfClassName + "Classification";
        classificationDto.setBmfClassName(classificationName);
        classificationDto.setDescriptionName(classificationMap.get(classificationName));
        this.toolLedgerClassificationService.saveOrUpdate(classificationDto);
        return InvokeResult.success();
    }


}
