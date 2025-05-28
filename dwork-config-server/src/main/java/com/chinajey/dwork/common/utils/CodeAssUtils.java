package com.chinajey.dwork.common.utils;

import com.chinajay.virgo.bmf.core.BmfAttribute;
import com.chinajay.virgo.bmf.core.BmfCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.tengnat.dwork.common.utils.CodeGenerator;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeAssUtils {

    public static void setCode(BmfObject bmfObject, String codeValue) {
        bmfObject.remove("code");
        BmfService bmfService = SpringUtils.getBean(BmfService.class);
        CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class);
        Map<String, Object> params = new HashMap<>();
        params.put("bmfClass", bmfObject.getBmfClassName());
        params.put("bmfAttribute", "code");
        params.put("status", true);
        List<BmfObject> bmfObjects = bmfService.find("codeRule", params);
        if (CollectionUtils.isEmpty(bmfObjects)) {
            throw new BusinessException("匹配不上编码规则：" + bmfObject.getBmfClassName());
        }
        BmfObject ruleBmfObject = bmfObjects.get(0);
        if (StringUtils.equals(ruleBmfObject.getString("codeMode"), "2")) {
            // 手动编码
            if (StringUtils.isBlank(codeValue)) {
                throw new BusinessException("手动编码时，编码不能为空");
            }
            Boolean codeEqBarCode = ruleBmfObject.getBoolean("codeEqBarCode");
            if (Boolean.TRUE.equals(codeEqBarCode)) {
                // 编码与条码一致，需要拼上条码标识
                bmfObject.put("code", ruleBmfObject.getString("codeIdentity") + codeValue);
            } else {
                bmfObject.put("code", codeValue);
            }
            BmfObject diskBmfObject = bmfService.findByUnique(bmfObject.getBmfClassName(), "code", bmfObject.getString("code"));
            if (diskBmfObject != null) {
                throw new BusinessException("生产编码失败，编码已存在：" + bmfObject.getString("code"));
            }
            return;
        }
        ruleBmfObject.autoRefresh();
        BmfAttribute codeAttribute = BmfCache.getBmfAttribute(bmfObject.getBmfClassName(), "code");
        codeGenerator.setCode(bmfObject.getBmfClassName(), bmfObject, ruleBmfObject, codeAttribute, true);
    }
}
