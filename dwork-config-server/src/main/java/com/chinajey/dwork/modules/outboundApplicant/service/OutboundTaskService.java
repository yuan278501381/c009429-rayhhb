package com.chinajey.dwork.modules.outboundApplicant.service;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.chinajey.dwork.common.enums.DocumentStatusEnum;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.tengnat.dwork.modules.logistics.service.LogisticsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OutboundTaskService {

    @Resource
    private BmfService bmfService;

    @Resource
    private LogisticsService logisticsService;

    @Resource
    private BusinessUtils businessUtils;

    /**
     * 取消出库任务
     *
     * @param ids
     */
    @Transactional
    public void cancel(List<Long> ids) {
        if (ids == null) {
            throw new RuntimeException("参数缺失[ids]");
        }
        for (Long id : ids) {
            BmfObject bmfObject = this.bmfService.find("outboundTask", id);
            if (bmfObject == null) {
                throw new BusinessException("出库任务不存在,id:" + id);
            }
            if (!DocumentStatusEnum.PENDING.getCode().equals(bmfObject.getString("documentStatus"))) {
                throw new BusinessException("单据状态为[" + DocumentStatusEnum.getEnum(bmfObject.getString("documentStatus")).getName() + "],不能取消");
            }
            //更新转态
            BmfObject update = new BmfObject("outboundTask");
            update.put("id", id);
            update.put("documentStatus", DocumentStatusEnum.CANCEL.getCode());
            bmfService.updateByPrimaryKeySelective(update);
            //关闭业务
            this.businessUtils.closeCurrentTaskByExt("GN10002", "extGn10002Id",Where.builder().restrictions(Collections.singletonList(Restriction.builder().conjunction(Conjunction.AND).attributeName("ext_outbound_task_code").operationType(OperationType.EQUAL).values(Collections.singletonList(bmfObject.getPrimaryKeyValue().toString())).build())).build(),true);
        }
    }
}
