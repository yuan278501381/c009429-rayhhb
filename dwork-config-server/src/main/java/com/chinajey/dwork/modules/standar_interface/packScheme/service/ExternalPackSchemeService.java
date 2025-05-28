package com.chinajey.dwork.modules.standar_interface.packScheme.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.CodeAssUtils;
import com.chinajey.dwork.common.utils.ValueUtils;
import com.chinajey.dwork.modules.standar_interface.packScheme.form.BarCodeRuleForm;
import com.chinajey.dwork.modules.standar_interface.packScheme.form.ExternalPackSchemeForm;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author erton.bi
 */
@Service
public class ExternalPackSchemeService {

    private static final String BMF_CLASS = "packScheme";

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;


    @Transactional(rollbackFor = Exception.class)
    public BmfObject saveOrUpdate(ExternalPackSchemeForm packSchemeForm) {
        packSchemeForm.valid();
        //转化数据
        JSONObject jsonObject = this.getJsonObject(packSchemeForm);
        BmfObject packScheme = this.businessUtils.getSyncBmfObject(BMF_CLASS, packSchemeForm.getCode());
        if (packScheme != null) {
            return this.update(jsonObject, packScheme);
        } else {
            return this.save(jsonObject);
        }
    }

    private BmfObject save(JSONObject jsonObject) {
        BmfObject packScheme = BmfUtils.genericFromJsonExt(jsonObject, BMF_CLASS);
        CodeAssUtils.setCode(packScheme, jsonObject.getString("externalDocumentCode"));
        this.bmfService.saveOrUpdate(packScheme);

        //下级包装方案编码
        BmfObject lowerPackScheme = (BmfObject) jsonObject.getJSONObject("lowerPackScheme");
        this.doPackLevels(lowerPackScheme, packScheme);

        //计算数量
        BmfObject bmfObject = this.bmfService.find("packScheme", packScheme.getPrimaryKeyValue());
        List<String> packLevelCodes = this.getPackLevels(bmfObject);
        if (packLevelCodes.contains(packScheme.getString("code"))) {
            throw new BusinessException("下级包装方案不允许关联当前包装方案");
        }
        packScheme.put("packageQuantity", countQuantityPerPackage(bmfObject));
        this.bmfService.updateByPrimaryKeySelective(packScheme);

        return packScheme;
    }

    private BmfObject update(JSONObject jsonObject, BmfObject packScheme) {
        jsonObject.put("id", packScheme.getPrimaryKeyValue());
        //更新包装方案
        BmfObject bmfObjectFrom = BmfUtils.genericFromJsonExt(jsonObject, "packScheme");
        BmfUtils.copyProperties(bmfObjectFrom, packScheme);
        this.bmfService.saveOrUpdate(packScheme);
        //删除之前的子项
        List<BmfObject> packLevels = this.bmfService.find("packLevel", Collections.singletonMap("packScheme", packScheme));
        if (!CollectionUtils.isEmpty(packLevels)) {
            packLevels.forEach(item -> this.bmfService.delete(item));
        }
        //下级包装方案编码
        BmfObject lowerPackScheme = (BmfObject) jsonObject.getJSONObject("lowerPackScheme");
        this.doPackLevels(lowerPackScheme, packScheme);
        //更新相应的子项
        List<BmfObject> packLevels1 = this.bmfService.find("packLevel", Collections.singletonMap("schemeCode", packScheme.getString("code")));
        if (!CollectionUtils.isEmpty(packLevels1)) {
            for (BmfObject bmfObject : packLevels1) {
                //下级数量取当前code对应主数据的下级数量
                bmfObject.put("lowQuantity", packScheme.getInteger("lowQuantity"));
                bmfObject.put("schemeName", packScheme.getString("name"));
                bmfObject.put("remark", packScheme.getString("remark"));
                bmfObject.put("unit", packScheme.getAndRefreshBmfObject("unit"));
                bmfObject.put("fileProcess", packScheme.getAndRefreshBmfObject("fileProcess"));
                this.bmfService.saveOrUpdate(bmfObject);
            }
        }

        //计算数量
        BmfObject bmfObject = this.bmfService.find("packScheme", packScheme.getPrimaryKeyValue());
        List<String> packLevelCodes = this.getPackLevels(bmfObject);
        if (packLevelCodes.contains(packScheme.getString("code"))) {
            throw new BusinessException("下级包装方案不允许关联当前包装方案");
        }
        packScheme.put("packageQuantity", countQuantityPerPackage(bmfObject));
        this.bmfService.updateByPrimaryKeySelective(packScheme);

        return packScheme;
    }


    private JSONObject getJsonObject(ExternalPackSchemeForm packSchemeForm) {
        //校验条码规则
        this.checkRules(packSchemeForm.getBarCodeRules());

        JSONObject jsonObject = new JSONObject();
        //物料信息
        if (StringUtils.isNotBlank(packSchemeForm.getMaterialCode())) {
            BmfObject material = this.businessUtils.getSyncBmfObject("material", packSchemeForm.getMaterialCode());
            if (material == null) {
                throw new BusinessException("包装方案未找到物料主数据" + packSchemeForm.getMaterialCode());
            }
            jsonObject.put("material", material);
        }
        //物料类型信息
        if (StringUtils.isNotBlank(packSchemeForm.getMaterialClassificationCode())) {
            BmfObject materialClassification = this.businessUtils.getSyncBmfObject("materialClassification", packSchemeForm.getMaterialClassificationCode());
            if (materialClassification == null) {
                throw new BusinessException("包装方案未找到物料类别主数据" + packSchemeForm.getMaterialClassificationCode());
            }
            jsonObject.put("materialClassification", materialClassification);
        }
        //单位信息
        if (StringUtils.isNotBlank(packSchemeForm.getUnitName())) {
            BmfObject unit = bmfService.findByUnique("measurementUnit", "name", packSchemeForm.getUnitName());
            if (unit == null) {
                throw new BusinessException("包装方案未找到单位主数据" + packSchemeForm.getUnitName());
            }
            jsonObject.put("unit", unit);
        }
        //工艺文件
        if (StringUtils.isNotBlank(packSchemeForm.getFileProcessCode())) {
            BmfObject fileProcess = this.businessUtils.getSyncBmfObject("fileProcess", packSchemeForm.getFileProcessCode());
            if (fileProcess == null) {
                throw new BusinessException("包装方案未找到工艺文件主数据" + packSchemeForm.getFileProcessCode());
            }
            jsonObject.put("fileProcess", fileProcess);
        }
        //下级包装方案
        if (StringUtils.isNotBlank(packSchemeForm.getLowerPackSchemeCode())) {
            BmfObject lowerPackScheme = this.businessUtils.getSyncBmfObject(BMF_CLASS, packSchemeForm.getLowerPackSchemeCode());
            if (lowerPackScheme == null) {
                throw new BusinessException("包装方案未找到下级包装方案主数据" + packSchemeForm.getLowerPackSchemeCode());
            }
            if (Arrays.asList(lowerPackScheme.getString("code"), lowerPackScheme.getString("externalDocumentCode")).contains(packSchemeForm.getCode())) {
                throw new BusinessException("下级包装方案不允许关联当前包装方案");
            }
            jsonObject.put("lowerPackScheme", lowerPackScheme);
        }

        //补充基础数据
        jsonObject.put("externalDocumentCode", packSchemeForm.getCode());
        jsonObject.put("name", packSchemeForm.getName());
        jsonObject.put("packageType", packSchemeForm.getPackageType());
        jsonObject.put("status", ValueUtils.getBoolean(packSchemeForm.getStatus()));
        jsonObject.put("defaultStatus", ValueUtils.getBoolean(packSchemeForm.getDefaultStatus()));
        jsonObject.put("lowQuantity", packSchemeForm.getLowQuantity());
        jsonObject.put("remark", packSchemeForm.getRemark());
        JsonUtils.jsonMergeExtFiled(packSchemeForm.getExtFields(), jsonObject);
        List<BmfObject> barCodeRules = new ArrayList<>();
        if (CollectionUtil.isNotEmpty(packSchemeForm.getBarCodeRules())) {
            for (BarCodeRuleForm barCodeRule : packSchemeForm.getBarCodeRules()) {
                BmfObject barCodeRuleBmfObject = BmfUtils.genericFromJsonExt(JSONObject.parseObject(JSON.toJSONString(barCodeRule)),"packBarCodeRule");
                barCodeRules.add(barCodeRuleBmfObject);
            }
            jsonObject.put("barCodeRules", barCodeRules);
        }
        return jsonObject;
    }

    private void checkRules(List<BarCodeRuleForm> barCodeRules) {
        if (CollectionUtil.isNotEmpty(barCodeRules)) {
            AtomicInteger serialNumber = new AtomicInteger();
            AtomicInteger dateNumber = new AtomicInteger();
            for (BarCodeRuleForm barCodeRule : barCodeRules) {
                //编码类型  1-日期; 2-流水号; 3-常量
                String codeType = barCodeRule.getCodeType();

                if ("1".equals(codeType)) {
                    dateNumber.incrementAndGet();
                    //系统时间
                    if ("1".equals(barCodeRule.getTimeType())) {
                        //归零依据：格式年月不能按日归零
                        if (StringUtils.contains(barCodeRule.getFormat(), "d") && "3".equals(barCodeRule.getZeroBasis())) {
                            throw new BusinessException("只有格式为年月日的日期才能按日归零");
                        }
                        if (StringUtils.contains(barCodeRule.getFormat(), "y") && "2".equals(barCodeRule.getZeroBasis())) {
                            throw new BusinessException("按年格式的日期只能按年归零");
                        }
                    } else if ("2".equals(barCodeRule.getTimeType())) {
                        //业务日期不能按日归零
                        if ("3".equals(barCodeRule.getZeroBasis())) {
                            throw new BusinessException("业务日期不能按日归零");
                        }
                        //归零依据：格式年月不能按日归零
                        if (StringUtils.contains(barCodeRule.getFormat(), "d") && "3".equals(barCodeRule.getZeroBasis())) {
                            throw new BusinessException("只有格式为年月日的日期才能按日归零");
                        }
                        if (StringUtils.contains(barCodeRule.getFormat(), "y") && "2".equals(barCodeRule.getZeroBasis())) {
                            throw new BusinessException("按年格式的日期只能按年归零");
                        }
                    } else {
                        throw new BusinessException("日期格式有误");
                    }


                }

                if ("2".equals(codeType)) {
                    serialNumber.incrementAndGet();
                    //补位符长度为1
                    String compCharacter = barCodeRule.getCompCharacter();
                    if (StringUtils.isNotBlank(compCharacter) && StringUtils.length(compCharacter) > 1) {
                        throw new BusinessException("补位符长度为1");
                    }
                    //流水号长度1-8
                    Integer length = barCodeRule.getLength();
                    if (length != null && (length > 8 || length < 1)) {
                        throw new BusinessException("流水号长度1-8");
                    }
                    //只有日期和业务实体才能成为流水号依据
                    if (barCodeRule.getSerialNumBasis() != null && barCodeRule.getSerialNumBasis()) {
                        throw new BusinessException("只有日期和业务实体才能成为流水号依据");
                    }
                }

                if ("3".equals(codeType)) {
                    //常量长度1-8
                    if (StringUtils.length(barCodeRule.getValue()) > 8 || StringUtils.length(barCodeRule.getValue()) < 1) {
                        throw new BusinessException("常量长度1-8");
                    }
                    if (barCodeRule.getSerialNumBasis() != null && barCodeRule.getSerialNumBasis()) {
                        throw new BusinessException("只有日期和业务实体才能成为流水号依据");
                    }
                }
            }
            //并且流水号必须有
            if (serialNumber.intValue() == 0) {
                throw new BusinessException("必须存在流水号");
            }
            //流水号只能选一次
            if (serialNumber.intValue() > 1) {
                throw new BusinessException("流水号只能选一次");
            }
            //日期只能选一次
            if (dateNumber.intValue() > 1) {
                throw new BusinessException("日期只能选一次");
            }
        }
    }

    private void doPackLevels(BmfObject lowerPackScheme, BmfObject packScheme) {
        if (lowerPackScheme != null) {
            BmfObject packLevel = new BmfObject("packLevel");
            //本包装数量取上级主数据中的下级数量
            packLevel.put("quantity", packScheme.getInteger("lowQuantity"));
            //下级数量取当前code对应主数据的下级数量
            packLevel.put("lowQuantity", lowerPackScheme.getInteger("lowQuantity"));
            packLevel.put("schemeCode", lowerPackScheme.getString("code"));
            packLevel.put("schemeName", lowerPackScheme.getString("name"));
            packLevel.put("remark", lowerPackScheme.getString("remark"));
            packLevel.put("unit", lowerPackScheme.getAndRefreshBmfObject("unit"));
            packLevel.put("fileProcess", lowerPackScheme.getAndRefreshBmfObject("fileProcess"));
            packLevel.put("packScheme", packScheme);
            this.bmfService.saveOrUpdate(packLevel);

            this.getPackLevels(lowerPackScheme);
            List<BmfObject> packLevels = lowerPackScheme.getList("packLevels");
            if (!CollectionUtils.isEmpty(packLevels)) {
                for (BmfObject packLevel1 : packLevels) {
                    packLevel1.remove("id");
                    packLevel1.put("packScheme", packScheme);
                    packLevel1.put("parent", packLevel);
                    String schemeCode = packLevel1.getString("schemeCode");
                    if (org.apache.commons.lang.StringUtils.isNotBlank(schemeCode)) {
                        BmfObject packScheme1 = this.bmfService.findByUnique("packScheme", "code", schemeCode);
                        if (packScheme1 != null) {
                            packLevel1.put("unit", packScheme1.getAndRefreshBmfObject("unit"));
                            packLevel1.put("fileProcess", packScheme1.getAndRefreshBmfObject("fileProcess"));
                        }
                    }
                    this.bmfService.saveOrUpdate(packLevel1);

                    this.saveChildren(packLevel1, packScheme, packLevel1);
                }
            }
        }
    }

    private List<String> getPackLevels(BmfObject bmfObject) {
        List<String> codes = new ArrayList<>();
        List<BmfObject> packLevels = bmfObject.getAndRefreshList("packLevels");
        List<BmfObject> parents = new ArrayList<>();
        for (Iterator<BmfObject> it = packLevels.iterator(); it.hasNext(); ) {
            BmfObject bo = it.next();
            if (org.apache.commons.lang3.StringUtils.isBlank(bo.getString("parent"))) {
                parents.add(bo);
                it.remove();
            }
        }
        for (BmfObject parent : parents) {
            this.buildTree(parent, packLevels, codes);
        }
        bmfObject.putUncheck("packLevels", parents);
        return codes;
    }

    private void saveChildren(BmfObject packLevel, BmfObject packScheme, BmfObject parent) {
        List<BmfObject> packLevels = packLevel.getList("children");
        if (!CollectionUtils.isEmpty(packLevels)) {
            for (BmfObject packLevel1 : packLevels) {
                packLevel1.remove("id");
                packLevel1.put("packScheme", packScheme);
                packLevel1.put("parent", parent);
                String schemeCode = packLevel1.getString("schemeCode");
                if (org.apache.commons.lang.StringUtils.isNotBlank(schemeCode)) {
                    BmfObject packScheme1 = this.bmfService.findByUnique("packScheme", "code", schemeCode);
                    if (packScheme1 != null) {
                        packLevel1.put("unit", packScheme1.getAndRefreshBmfObject("unit"));
                        packLevel1.put("fileProcess", packScheme1.getAndRefreshBmfObject("fileProcess"));
                    }
                }
                bmfService.saveOrUpdate(packLevel1);

                this.saveChildren(packLevel1, packScheme, packLevel1);
            }
        }
    }

    public void buildTree(BmfObject parent, List<BmfObject> packLevels, List<String> codes) {
        parent.remove("ownerCode");
        parent.remove("printTemplate");
        parent.remove("isDelete");
        parent.remove("deptId");
        parent.remove("ownerId");
        parent.remove("ownerName");
        parent.remove("ownerId");
        parent.remove("ownerId");
        parent.remove("packScheme");
        parent.put("createTime", parent.getDate("createTime").getTime());
        parent.put("updateTime", parent.getDate("updateTime").getTime());

        BmfObject unit = parent.getAndRefreshBmfObject("unit");
        if (unit != null) {
            parent.put("unitName", unit.getString("name"));
        }
        parent.remove("unit");

        BmfObject fileProcess = parent.getAndRefreshBmfObject("fileProcess");
        if (fileProcess != null) {
            parent.put("fileProcessCode", fileProcess.getString("code"));
            parent.put("fileProcessName", fileProcess.getString("name"));
        }
        parent.remove("fileProcess");

        List<BmfObject> children = new ArrayList<>();
        for (BmfObject packLevel : packLevels) {
            String schemeCode = packLevel.getString("schemeCode");
            codes.add(schemeCode);

            BmfObject bmfObject = packLevel.getAndRefreshBmfObject("parent");

            parent.put("createTime", parent.getDate("createTime").getTime());
            parent.put("updateTime", parent.getDate("updateTime").getTime());

            BmfObject unit1 = packLevel.getAndRefreshBmfObject("unit");
            if (unit1 != null) {
                packLevel.put("unitName", unit1.getString("name"));
            }
            packLevel.remove("unit");

            BmfObject fileProcess1 = packLevel.getAndRefreshBmfObject("fileProcess");
            if (fileProcess1 != null) {
                packLevel.put("fileProcessCode", fileProcess1.getString("code"));
                packLevel.put("fileProcessName", fileProcess1.getString("name"));
            }
            packLevel.remove("fileProcess");

            if (bmfObject == null) {
                continue;
            }
            if (bmfObject.getPrimaryKeyValue().equals(parent.getPrimaryKeyValue())) {
                children.add(packLevel);
                this.buildTree(packLevel, packLevels, codes);
            }
            packLevel.remove("parent");
        }
        parent.putUncheck("children", children);
        parent.remove("parent");
    }

    private BigDecimal countQuantityPerPackage(BmfObject packScheme) {
        List<BmfObject> packLevels = packScheme.getList("packLevels");
        if (CollectionUtils.isEmpty(packLevels)) {
            BigDecimal lowQuantity = packScheme.getBigDecimal("lowQuantity");
            if (lowQuantity != null) {
                return lowQuantity;
            } else {
                return BigDecimal.ZERO;
            }
        }
        return this.countLevelQuantity(packLevels);
    }

    private BigDecimal countLevelQuantity(List<BmfObject> packLevels) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BmfObject packLevel : packLevels) {
            BigDecimal quantity = packLevel.getBigDecimal("quantity");
            if (quantity == null) {
                throw new BusinessException("包装层级:本包装数量为空");
            }
            List<BmfObject> children = packLevel.getList("children");
            if (children != null && children.size() > 0) {
                sum = sum.add(quantity.multiply(this.countLevelQuantity(children)));
            } else {
                BigDecimal lowQuantity = packLevel.getBigDecimal("lowQuantity");
                if (lowQuantity == null) {
                    throw new BusinessException("最小层级包装方案下级数量为0");
                }
                sum = sum.add(quantity.multiply(lowQuantity));
            }
        }
        return sum;
    }
}
