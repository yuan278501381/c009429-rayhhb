package com.chinajey.dwork.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.chinajay.virgo.bmf.core.BmfAttribute;
import com.chinajay.virgo.bmf.core.BmfCache;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.*;
import com.chinajay.virgo.utils.BusinessUtil;
import com.chinajey.application.common.exception.BusinessException;
import com.tengnat.dwork.common.constant.BmfAttributeConst;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import com.tengnat.dwork.modules.logistics.service.SceneInstanceService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BusinessUtils {

    @Resource
    BmfService bmfService;

    @Resource
    LogisticsService logisticsService;

    @Resource
    SceneInstanceService sceneInstanceService;

    /**
     * 关闭任务(只关闭一个节点)
     *
     * @param gnBmfClass       节点编码
     * @param dataSourceCode   来源单据编号
     * @param revertSourceTask 是否还原源任务
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void closeCurrentTaskByDataSourceCode(String gnBmfClass, String dataSourceCode, Boolean... revertSourceTask) {
        List<BmfObject> list = bmfService.find(gnBmfClass, Where.builder().restrictions(Arrays.asList(
                Restriction.builder()
                        .bmfClassName(gnBmfClass)
                        .conjunction(Conjunction.AND)
                        .attributeName("dataSourceCode")
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(dataSourceCode))
                        .build(),
                Restriction.builder()
                        .bmfClassName(gnBmfClass)
                        .conjunction(Conjunction.AND)
                        .attributeName("logisticsStatus")
                        .operationType(OperationType.IN)
                        .values(Arrays.asList("1", "2"))
                        .build()
        )).build());

        if (CollectionUtils.isNotEmpty(list)) {
            list.forEach(l -> {
                BmfObject bmfObject = new BmfObject(l.getBmfClassName());
                bmfObject.put("id", l.getPrimaryKeyValue());
                bmfObject.put("logisticsStatus", "4");
                bmfService.updateByPrimaryKeySelective(bmfObject);
                logisticsService.taskIdCloseLogisticsTransaction(bmfObject, revertSourceTask.length != 0 && revertSourceTask[0], revertSourceTask.length > 1 && revertSourceTask[1]);
            });
        }
    }


    /**
     * 关闭所有的任务 包括已完成的
     *
     * @param gnBmfClass       节点编码
     * @param dataSourceCode   来源编码
     * @param revertSourceTask 是否还原源任务
     */
    @Deprecated
    @Transactional(rollbackFor = Exception.class)
    public void closeCurrentAllTaskByDataSourceCode(String gnBmfClass, String dataSourceCode, Boolean... revertSourceTask) {
        List<BmfObject> list = bmfService.find(gnBmfClass, Where.builder().restrictions(Arrays.asList(
                Restriction.builder()
                        .bmfClassName(gnBmfClass)
                        .conjunction(Conjunction.AND)
                        .attributeName("dataSourceCode")
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(dataSourceCode))
                        .build(),
                Restriction.builder()
                        .bmfClassName(gnBmfClass)
                        .conjunction(Conjunction.AND)
                        .attributeName("logisticsStatus")
                        .operationType(OperationType.IN)
                        .values(Arrays.asList("0", "1", "2", "3"))
                        .build()
        )).build());

        if (CollectionUtils.isNotEmpty(list)) {
            list.forEach(l -> {
                BmfObject bmfObject = new BmfObject(l.getBmfClassName());
                bmfObject.put("id", l.getPrimaryKeyValue());
                bmfObject.put("logisticsStatus", "4");
                bmfService.updateByPrimaryKeySelective(bmfObject);
                logisticsService.taskIdCloseLogisticsTransaction(bmfObject, revertSourceTask.length != 0 && revertSourceTask[0], revertSourceTask.length > 1 && revertSourceTask[1]);
            });
        }
    }
//    public void closeCurrentAllTaskByDataSourceCode(String gnBmclass, String dataSourceCode) {
//        this.closeCurrentTaskByDataSourceCode(gnBmclass, dataSourceCode);
//    }

    /**
     * 关闭任务(只关闭一个节点)
     *
     * @param gnBmfClass       节点编码
     * @param value            来源单据编号
     * @param revertSourceTask 是否还原源任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeCurrentTask(String gnBmfClass, String bmfAttribute, String value, Boolean... revertSourceTask) {
        List<BmfObject> list = bmfService.find(gnBmfClass, Where.builder().restrictions(Arrays.asList(
                Restriction.builder()
                        .bmfClassName(gnBmfClass)
                        .conjunction(Conjunction.AND)
                        .attributeName(bmfAttribute)
                        .operationType(OperationType.EQUAL)
                        .values(Collections.singletonList(value))
                        .build(),
                Restriction.builder()
                        .bmfClassName(gnBmfClass)
                        .conjunction(Conjunction.AND)
                        .attributeName("logisticsStatus")
                        .operationType(OperationType.IN)
                        .values(Arrays.asList("1", "2"))
                        .build()
        )).build());

        if (CollectionUtils.isNotEmpty(list)) {
            list.forEach(l -> {
                BmfObject bmfObject = new BmfObject(l.getBmfClassName());
                bmfObject.put("id", l.getPrimaryKeyValue());
                bmfObject.put("logisticsStatus", "4");
                bmfService.updateByPrimaryKeySelective(bmfObject);
                logisticsService.taskIdCloseLogisticsTransaction(bmfObject, revertSourceTask.length != 0 && revertSourceTask[0], revertSourceTask.length > 1 && revertSourceTask[1]);
            });
        }
    }

    /**
     * 通过扩展字段关闭任务
     *
     * @param gnBmfClass       gnBmfClass
     * @param bmfAttribute     主表关联字段
     * @param where            条件
     * @param revertSourceTask 是否还原源任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeCurrentTaskByExt(String gnBmfClass, String bmfAttribute, Where where, Boolean... revertSourceTask) {
        List<BmfObject> list = bmfService.find(gnBmfClass + "Ext", where);
        if (CollectionUtils.isNotEmpty(list)) {
            list.forEach(l -> {
                BmfObject bmfObject = bmfService.findOne(gnBmfClass, Where.builder().restrictions(Arrays.asList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName("id")
                                .operationType(OperationType.EQUAL)
                                .values(Collections.singletonList(l.getLong(bmfAttribute)))
                                .build(),
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName("logisticsStatus")
                                .operationType(OperationType.IN)
                                .values(Arrays.asList("1", "2"))
                                .build()
                )).build());
                if (bmfObject != null) {
                    BmfObject update = new BmfObject(gnBmfClass);
                    update.put("id", bmfObject.getPrimaryKeyValue());
                    update.put("logisticsStatus", "4");
                    bmfService.updateByPrimaryKeySelective(update);
                    logisticsService.taskIdCloseLogisticsTransaction(bmfObject, revertSourceTask.length != 0 && revertSourceTask[0], revertSourceTask.length > 1 && revertSourceTask[1]);
                }

            });
        }
    }

    /**
     * 通过扩展字段查询任务
     */
    public List<BmfObject> findTaskByExtWhere(String gnBmclass, String bmfAttribute, Where where) {
        List<BmfObject> result = new ArrayList<>();
        List<BmfObject> list = bmfService.find(gnBmclass + "Ext", where);
        if (CollectionUtils.isNotEmpty(list)) {
            list.forEach(l -> {
                BmfObject bmfObject = bmfService.findOne(gnBmclass, Where.builder().restrictions(Arrays.asList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName("id")
                                .operationType(OperationType.EQUAL)
                                .values(Collections.singletonList(l.getLong(bmfAttribute)))
                                .build(),
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .attributeName("logisticsStatus")
                                .operationType(OperationType.IN)
                                .values(Arrays.asList("0", "1", "2"))
                                .build()
                )).build());
                if (bmfObject != null) {
                    result.add(bmfObject);
                }

            });
        }
        return result;
    }

    /**
     * id降序取第一个单据
     *
     * @param params sourceCode
     * @return {@link BmfObject}
     */
    public BmfObject findOrder(Map<String, Object> params, String bmfClass) {
        if (CollectionUtil.isEmpty(params)) {
            return null;
        }
        //根据id 降序 取第一个 就是最新的单据信息
        Order order = BusinessUtil.getOrderBy("id", FieldSort.DESC);
        List<Restriction> restrictions = params.keySet().stream().map(key -> Restriction.builder().conjunction(Conjunction.AND)
                .attributeName(key)
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(params.get(key)))
                .build()).collect(Collectors.toList());
        Where where = Where.builder().order(order).restrictions(restrictions).build();

        return bmfService.findOne(bmfClass, where);
    }


    /**
     * @param id               主表id
     * @param pageable         分页
     * @param bmfClassItemName 子表业务名称
     * @param bmfClassMainId   子表关联主表attributeName
     * @return {@link List}<{@link BmfObject}>
     */
    public List<BmfObject> findItemPage(Long id, Pageable pageable, String bmfClassItemName, String bmfClassMainId) {
        List<CombRestriction> combRestrictions = new ArrayList<>();
        //子表分页查询
        List<Restriction> restrictions = new ArrayList<>();
        CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
        restrictions.add(Restriction.builder()
                .bmfClassName(bmfClassItemName)
                .operationType(OperationType.EQUAL)
                .attributeName(bmfClassMainId)
                .values(Collections.singletonList(id))
                .build());
        keywordCombRestriction.setRestrictions(restrictions);
        combRestrictions.add(keywordCombRestriction);
        //查询条件
        Where where = Where.builder()
                .combRestrictions(combRestrictions)
                .order(Order.builder()
                        .sortFields(Collections.singletonList(
                                SortField.builder()
                                        .bmfClassName(bmfClassItemName)
                                        .bmfAttributeName(BmfAttributeConst.ID)
                                        .fieldSort(FieldSort.ASC)
                                        .build()
                        )).build())
                .build();
        Page<BmfObject> page = this.bmfService.findPage(bmfClassItemName, where, pageable);
        List<BmfObject> bmfObjectItems = page.getContent();
        for (BmfObject bmfObjectItem : bmfObjectItems) {
            bmfObjectItem.autoRefresh();
        }
        return bmfObjectItems;
    }

    /**
     * 更新单据状态
     *
     * @param bmfObject bmfObject
     * @param status    status
     */
    public void updateStatus(BmfObject bmfObject, String status) {
        BmfObject update = BmfObject.reNewUpdateObj(bmfObject);
        update.put("documentStatus", status);
        bmfService.updateByPrimaryKeySelective(update);
    }

    /**
     * 获取同步数据对象
     * 如果bmfClass有外部编码属性，会根据外部编码查询。
     * 如果没有外部编码属性，会根据code查询。
     * 如果外部编码查询不到数据，也会根据code查询
     */
    public BmfObject getSyncBmfObject(String bmfClass, String codeValue) {
        // 匹配外部编码
        BmfAttribute externalCodeAttribute = BmfCache.getBmfAttribute(bmfClass, "externalDocumentCode");
        BmfObject bmfObject = null;
        if (externalCodeAttribute != null) {
            bmfObject = this.bmfService.findByUnique(bmfClass, "externalDocumentCode", codeValue);
        }
        if (bmfObject != null) {
            return bmfObject;
        }
        // 如果存在code就匹配code，不存在code就匹配id
        BmfAttribute codeAttribute = BmfCache.getBmfAttribute(bmfClass, "code");
        if (codeAttribute != null) {
            return this.bmfService.findByUnique(bmfClass, "code", codeValue);
        }
        long id;
        try {
            id = Long.parseLong(codeValue);
        } catch (Exception e) {
            throw new BusinessException("无法匹配ID值：" + codeValue);
        }
        return this.bmfService.find(bmfClass, id);
    }

    public BmfObject getPackSchemeBmfObject(BmfObject production) {
        HashMap<String, Object> map = new HashMap<>(3);
        map.put("material", production.getPrimaryKeyValue());
        map.put("defaultStatus", true);
        map.put("status", true);
        return this.bmfService.findOne("packScheme", map);
    }

    public BmfObject getMeasurementUnitBmfObject(String name) {
        return this.bmfService.findByUnique("measurementUnit", "name", name);
    }
}
