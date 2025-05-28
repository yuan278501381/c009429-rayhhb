package com.chinajey.dwork.modules.warehousingApplicant.dto;

import com.chinajay.virgo.bmf.obj.BmfObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WriteBackResult {

    /**
     * 单据信息
     */
    private BmfObject order;

    /**
     * 单据的明细
     */
    private List<BmfObject> allDetails;

    /**
     * 被反写的明细
     */
    private List<BmfObject> details;
}
