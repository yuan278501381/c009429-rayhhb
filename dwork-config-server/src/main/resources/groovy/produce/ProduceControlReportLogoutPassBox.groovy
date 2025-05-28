package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.tengnat.dwork.modules.manufacturev2.domain.DomainPassBoxLoginOrOut
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.domain.form.SceneModuleProduceReportingForm
import com.tengnat.dwork.modules.script.abstracts.ProduceControlGroovyClass

import java.util.stream.Collectors

/**
 * 报工控制
 * 报工自动登出周转箱
 */
class ProduceControlReportLogoutPassBox extends ProduceControlGroovyClass {
    @Override
    void submit(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        List<String> passBoxCodes = jsonObject
                .getJSONArray("reportPassBoxes")
                .toJavaList(SceneModuleProduceReportingForm.PassBox.class)
                .stream()
                .map(it -> it.getPassBoxCode())
                .collect(Collectors.toList())
        new DomainPassBoxLoginOrOut(stationReal).logout(passBoxCodes, false, true)
    }
}