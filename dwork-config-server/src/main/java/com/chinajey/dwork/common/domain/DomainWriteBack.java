package com.chinajey.dwork.common.domain;

import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.WriteBackUtils;
import com.chinajey.dwork.modules.warehousingApplicant.dto.StoreDimension;
import com.chinajey.dwork.modules.warehousingApplicant.dto.WriteBackResult;
import com.chinajey.dwork.modules.warehousingApplicant.dto.StoreWriteField;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public class DomainWriteBack {

    private final BmfService bmfService;

    public DomainWriteBack() {
        this.bmfService = SpringUtils.getBean(BmfService.class);
    }

    public WriteBackResult writeBackStoreDetails(String applicantCode, BigDecimal quantity, StoreDimension dim, StoreWriteField field) {
        BmfObject warehousingApplicant = this.bmfService.findByUnique("warehousingApplicant", "code", applicantCode);
        if (warehousingApplicant == null) {
            throw new BusinessException("入库申请单[" + applicantCode + "]不存在");
        }
        List<BmfObject> allDetails = warehousingApplicant.getAndRefreshList("warehousingApplicantIdAutoMapping");
        List<BmfObject> applicantDetails = allDetails
                .stream()
                .filter(it -> StringUtils.equals(it.getString("materialCode"), dim.getMaterialCode())
                        && StringUtils.equals(it.getString("targetWarehouseCode"), dim.getWarehouseCode()))
                .collect(Collectors.toList());
        // 反写入库申请单明细的已确认、待确认数量
        WriteBackUtils.writeBack(applicantDetails, quantity, field.getDis(), field.getUnDis(), field.getInc());

        List<BmfObject> details = applicantDetails
                .stream()
                .filter(WriteBackUtils::isRealWriteBack)
                .collect(Collectors.toList());
        return new WriteBackResult(warehousingApplicant, allDetails, details);
    }
}
