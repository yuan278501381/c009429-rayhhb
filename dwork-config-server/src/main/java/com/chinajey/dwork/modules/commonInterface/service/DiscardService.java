package com.chinajey.dwork.modules.commonInterface.service;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import com.chinajey.application.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class DiscardService {

    @Resource
    private BmfService bmfService;
    public Object getDiscardAsUselessCauseGroup( Integer page, Integer size, String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        List<Restriction> restrictions = new ArrayList<>();
        if (StringUtils.isNotBlank(keyword)){
            restrictions.add(Restriction.builder()
                    .bmfClassName("discardAsUselessCauseGroup")
                    .conjunction(Conjunction.AND)
                    .attributeName("name")
                    .columnName("name")
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
            restrictions.add(Restriction.builder()
                    .bmfClassName("discardAsUselessCauseGroup")
                    .conjunction(Conjunction.OR)
                    .attributeName("code")
                    .columnName("code")
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
        }
        Where where = Where.builder().restrictions(restrictions).build();
        Page<BmfObject> pageResult = this.bmfService.findPage("discardAsUselessCauseGroup",where, pageable);
        return pageResult;
    }

    public Object getDiscardAsUselessCause(String classCode, Integer page, Integer size, String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        if (StringUtils.isBlank(classCode)) {
            throw new BusinessException("报废分类编码不能为空!");
        }
        BmfObject bmfObject = bmfService.findByUnique("discardAsUselessCauseGroup", "code", classCode);
        if (bmfObject == null) {
            throw new BusinessException("报废分类对应数据不存在,编码:"+classCode);
        }
        List<Restriction> restrictions = new ArrayList<>();
        //只查该类别下启用的物料
        restrictions.add(Restriction.builder()
                .bmfClassName("discardAsUselessCause")
                .conjunction(Conjunction.AND)
                .attributeName("discardAsUselessCauseGroup")
                .columnName("discardAsUselessCauseGroup")
                .operationType(OperationType.EQUAL)
                .values(Collections.singletonList(bmfObject.getPrimaryKeyValue()))
                .build());
        if (StringUtils.isNotBlank(keyword)){
            restrictions.add(Restriction.builder()
                    .bmfClassName("discardAsUselessCause")
                    .conjunction(Conjunction.AND)
                    .attributeName("name")
                    .columnName("name")
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
            restrictions.add(Restriction.builder()
                    .bmfClassName("discardAsUselessCause")
                    .conjunction(Conjunction.OR)
                    .attributeName("code")
                    .columnName("code")
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
        }
        Where where = Where.builder().restrictions(restrictions).build();
        Page<BmfObject> pageResult = this.bmfService.findPage("discardAsUselessCause",where, pageable);
        return pageResult;
    }

    public Object getWorkGroup(Integer page, Integer size, String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        List<Restriction> restrictions = new ArrayList<>();
        if (StringUtils.isNotBlank(keyword)){
            restrictions.add(Restriction.builder()
                    .bmfClassName("workGroup")
                    .conjunction(Conjunction.AND)
                    .attributeName("name")
                    .columnName("name")
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
            restrictions.add(Restriction.builder()
                    .bmfClassName("workGroup")
                    .conjunction(Conjunction.OR)
                    .attributeName("code")
                    .columnName("code")
                    .operationType(OperationType.LIKE)
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
        }
        Where where = Where.builder().restrictions(restrictions).build();
        Page<BmfObject> pageResult = this.bmfService.findPage("workGroup",where, pageable);
        return pageResult;
    }
}
