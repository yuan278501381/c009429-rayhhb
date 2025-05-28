package com.chinajey.dwork.modules.exceptionTransferPassBox.service;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.BusinessUtil;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * @description: 异常转出周转箱
 * @author: ZSL
 * @date: 2025.05.26 11:47
 */
@Service
public class ExceptionPassBoxService {
    public static final String BMF_CLASS = "exceptionTransferPassBox";

    @Resource
    private BmfService bmfService;

    @Transactional(rollbackFor = Exception.class)
    public void remove(List<Long> ids) {
        if (CollectionUtil.isEmpty(ids)) {
            throw new BusinessException("异常转出周转箱ID不能为空");
        }
        for (Long id : ids) {
            BmfObject exceptionTransferPassBox = this.bmfService.find(BMF_CLASS, id);
            if (exceptionTransferPassBox == null) {
                throw new BusinessException("异常转出周转箱不存在,ID:" + id);
            }
            bmfService.delete(BMF_CLASS, id);
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public void submit(JSONObject jsonObject) {
        JSONArray idsArr = jsonObject.getJSONArray("ids");
        List<Long> ids = idsArr.toJavaList(Long.class);
        String warehouseCode = jsonObject.getString("warehouseCode");
    }
}
