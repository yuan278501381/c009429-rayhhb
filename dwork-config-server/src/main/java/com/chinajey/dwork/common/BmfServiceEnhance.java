package com.chinajey.dwork.common;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class BmfServiceEnhance {

    @Resource
    private BmfService bmfService;

    public List<BmfObject> findIn(String bmfClass, String attribute, List<Object> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new ArrayList<>();
        }
        return this.bmfService.find(bmfClass,
                Where.builder()
                        .restrictions(
                                Collections.singletonList(
                                        Restriction.builder()
                                                .conjunction(Conjunction.AND)
                                                .operationType(OperationType.IN)
                                                .attributeName(attribute)
                                                .values(values)
                                                .build()
                                )
                        ).build()
        );
    }
}
