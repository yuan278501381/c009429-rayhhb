package com.chinajey.dwork.common.utils;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.core.BmfAttribute;
import com.chinajay.virgo.bmf.core.BmfCache;
import com.chinajay.virgo.bmf.core.BmfClass;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BmfUtils;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.AssistantUtils;
import com.chinajey.dwork.common.FillUtils;
import com.chinajey.dwork.common.dto.MiddleDto;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.enums.SourceSystemEnum;
import com.chinajey.dwork.common.form.BindForm;
import com.chinajey.dwork.common.form.Relation;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExtractUtils {

    /**
     * 基础数据的通用保存或更新方法
     * 可能覆盖不了所有基础数据，调用前需要自己判断是否符合自己的业务逻辑
     */
    public static BmfObject commonSaveOrUpdate(String bmfClass, JSONObject jsonObject) {
        return commonSaveOrUpdate(bmfClass, jsonObject, null, null);
    }

    /**
     * 基础数据的通用保存或更新方法
     * 可能覆盖不了所有基础数据，调用前需要自己判断是否符合自己的业务逻辑
     */
    public static BmfObject commonSaveOrUpdate(String bmfClass, JSONObject jsonObject, Function<JSONObject, BmfObject> save, BiConsumer<BmfObject, JSONObject> update) {
        BmfService bmfService = SpringUtils.getBean(BmfService.class);
        BusinessUtils businessUtils = SpringUtils.getBean(BusinessUtils.class);
        String externalDocumentCode = jsonObject.getString("externalDocumentCode");
        BmfObject bmfObject = businessUtils.getSyncBmfObject(bmfClass, externalDocumentCode);
        if (bmfObject == null) {
            if (save == null) {
                jsonObject.remove("code");
                bmfObject = BmfUtils.genericFromJsonExt(jsonObject, bmfClass);
                CodeAssUtils.setCode(bmfObject, externalDocumentCode);
                bmfService.saveOrUpdate(bmfObject);
            } else {
                bmfObject = save.apply(jsonObject);
            }
        } else {
            if (update == null) {
                String code = bmfObject.getString("code");
                BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
                bmfObject.put("code", code);
                bmfService.saveOrUpdate(bmfObject);
            } else {
                update.accept(bmfObject, jsonObject);
            }
        }
        return bmfObject;
    }

    /**
     * 处理绑定对象
     */
    public static List<BindForm> commonBindRelations(List<Relation> relations) {
        return commonBindRelations(relations, new ArrayList<>());
    }

    /**
     * 处理绑定对象
     * Relation.type必须在types中，如果types为空，则所有type都支持
     */
    public static List<BindForm> commonBindRelations(List<Relation> relations, List<String> types) {
        if (CollectionUtils.isEmpty(relations)) {
            return new ArrayList<>();
        }
        List<BindForm> bindForms = new ArrayList<>();
        BusinessUtils businessUtils = SpringUtils.getBean(BusinessUtils.class);
        for (Relation relation : relations) {
            if (CollectionUtils.isNotEmpty(types)) {
                if (!types.contains(relation.getType())) {
                    throw new BusinessException("不支持的关联对象类型：" + relation.getType());
                }
            }
            BmfObject b = businessUtils.getSyncBmfObject(relation.getType(), relation.getCode());
            if (b == null) {
                throw new BusinessException("关联对象[" + relation.getType() + " - " + relation.getCode() + "]不存在");
            }
            BindForm form = new BindForm();
            BeanUtils.copyProperties(relation, form);
            form.setName(b.getString("name"));
            bindForms.add(form);
        }
        return bindForms;
    }

    /**
     * 单据明细的修改逻辑 - 可能覆盖不了所有单据，调用前需要自己判断是否符合自己的业务逻辑
     *
     * @param bmfObject  数据库存在的单据
     * @param jsonObject 前端传递的单据数据
     * @param detailAttr 明细行的key
     * @param consumer   自定义逻辑
     */
    public static BmfObject commonOrderUpdate(BmfObject bmfObject, JSONObject jsonObject, String detailAttr, Consumer<List<JSONObject>> consumer) {
        List<JSONObject> details = jsonObject.getJSONArray(detailAttr).toJavaList(JSONObject.class);
        if (CollectionUtils.isEmpty(details)) {
            throw new BusinessException("单据明细不能为空");
        }
        consumer.accept(details);
        // 状态判断
        String status = bmfObject.getString("documentStatus");
        if (!DocumentStatusEnum.whetherUpdate(status)) {
            throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(status).getName() + "],不能修改");
        }
        List<BmfObject> diskDetails = bmfObject.getAndRefreshList(detailAttr);
        if (StringUtils.equals(SourceSystemEnum.DWORK.getCode(), jsonObject.getString("sourceSystem"))) {
            new LineNumUtils().lineNumHandle(diskDetails, details);
        }
        ExtractUtils.validateLineNum(details, "preDocumentCode");
        String code = bmfObject.getString("code");
        BmfUtils.bindingAttributeForEXT(bmfObject, jsonObject);
        // 保证编码不变
        bmfObject.put("code", code);
        FillUtils.fillOperator(bmfObject);
        if (StringUtils.equals(status, DocumentStatusEnum.CANCEL.getCode())) {
            bmfObject.put("documentStatus", DocumentStatusEnum.UNTREATED.getCode());
        }
        BmfService bmfService = SpringUtils.getBean(BmfService.class);
        bmfService.saveOrUpdate(bmfObject);
        return bmfObject;
    }

    /**
     * 校验行号 - 调用时记得看代码，校验逻辑是否符合自己的需求
     * 通常是单据的明细的某个字段与行号组成唯一标识
     *
     * @param details   单据的明细
     * @param attribute 唯一标识的属性
     */
    public static void validateLineNum(List<JSONObject> details, String attribute) {
        Set<String> sets = new HashSet<>();
        for (JSONObject detail : details) {
            String lineNum = detail.getString("lineNum");
            if (StringUtils.isBlank(lineNum)) {
                throw new BusinessException("行号不能为空");
            }
            Object o = detail.get(attribute);
            String pj = "";
            if (o != null) {
                pj = o.toString();
            }
            boolean success = sets.add(lineNum + AssistantUtils.SPLIT + pj);
            if (!success) {
                throw new BusinessException("单据明细行号不能重复");
            }
        }
    }

    /**
     * 保持或者修改中间表的数据
     *
     * @param bmfClass  中间表的bmfClass
     * @param manyCodes 多方的编码集合
     * @param oneId     一方的ID
     * @param oneAttr   一方的属性名
     * @param manyAttr  多方的属性名
     */
    public static void saveOrUpdateMiddleTable(String bmfClass, List<String> manyCodes, long oneId, String oneAttr, String manyAttr) {
        BmfClass info = BmfCache.getBmfClass(bmfClass);
        if (info == null) {
            throw new BusinessException("模型" + bmfClass + "不存在");
        }
        BmfAttribute one = info.getBmfAttribute(oneAttr);
        if (one == null) {
            throw new BusinessException("模型属性" + oneAttr + "不存在");
        }
        BmfAttribute many = info.getBmfAttribute(manyAttr);
        if (many == null) {
            throw new BusinessException("模型属性" + manyAttr + "不存在");
        }
        String reference = many.getReferenceBmfClassName();
        if (StringUtils.isBlank(reference)) {
            throw new BusinessException("关联模型" + bmfClass + "." + manyAttr + "不存在");
        }
        JdbcTemplate jdbcTemplate = SpringUtils.getBean(JdbcTemplate.class);
        String tableName = info.getTableName();
        if (CollectionUtils.isEmpty(manyCodes)) {
            jdbcTemplate.update("delete from " + tableName + " where " + one.getColumnName() + " = ?", oneId);
            return;
        }
        BusinessUtils businessUtils = SpringUtils.getBean(BusinessUtils.class);
        List<BmfObject> manyBmfObjects = new ArrayList<>();
        for (String manyCode : manyCodes) {
            BmfObject manyBmfObject = businessUtils.getSyncBmfObject(reference, manyCode);
            if (manyBmfObject == null) {
                throw new BusinessException(many.getLabel() + "信息[" + manyCode + "]不存在");
            }
            manyBmfObjects.add(manyBmfObject);
        }
        List<MiddleDto> middleInfos = jdbcTemplate.query(
                "select " + one.getColumnName() + " as oneId, " + many.getColumnName() + " as manyId from " + tableName + " where " + one.getColumnName() + " = ?",
                new Object[]{oneId},
                new BeanPropertyRowMapper<>(MiddleDto.class)
        );
        // 需要删除
        List<Long> removed = middleInfos.stream()
                .map(MiddleDto::getManyId)
                .filter(a -> manyBmfObjects.stream().noneMatch(b -> Objects.equals(a, b.getPrimaryKeyValue())))
                .collect(Collectors.toList());
        // 需要新增
        List<Long> added = manyBmfObjects.stream()
                .map(BmfObject::getPrimaryKeyValue)
                .filter(b -> middleInfos.stream().noneMatch(a -> Objects.equals(a.getManyId(), b)))
                .collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(removed)) {
            for (Long removeId : removed) {
                jdbcTemplate.update("delete from " + tableName + " where " + one.getColumnName() + " = ? and " + many.getColumnName() + " = ?", oneId, removeId);
            }
        }
        if (CollectionUtils.isNotEmpty(added)) {
            for (Long addId : added) {
                jdbcTemplate.update("insert into " + tableName + " (" + one.getColumnName() + ", " + many.getColumnName() + ") values (?, ?)", oneId, addId);
            }
        }
    }
}
