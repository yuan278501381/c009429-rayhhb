package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ValidateRuleResult
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.script.abstracts.ProduceValidateGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

import java.text.SimpleDateFormat
import java.time.LocalTime


class ProduceValidateLoginEquipmentCheck extends ProduceValidateGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean("basicGroovyService")

    @Override
    ValidateRuleResult login(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        def equipmentCode = extractEquipmentCode(jsonObject)
        def stationCode = stationReal?.getStationCode()

        if (!equipmentCode) {
            throw new BusinessException("工位【${stationCode}】必须登记设备！")
        }

        def now = new Date()
        def shift = getCurrentShift(now)
        if (shift == null) {
            throw new BusinessException("当前时间不在任何有效班次范围内，请检查班次主数据设置。")
        }

        def timeRange = getShiftDateTimeRange(now, shift.begin, shift.end)

        def sql = """
            SELECT 1
            FROM self_equipment_tally_sheet t
            WHERE t.is_delete = false
              AND t.status = 'completed'
              AND t.handle_result = 'pass'
              AND t.transaction_code = 'TR0003'
              AND t.ledger_code = '${equipmentCode}'
              AND t.completion_time BETWEEN '${timeRange.begin}' AND '${timeRange.end}'
        """

        def result = basicGroovyService.findOne(sql)
        if (!result) {
            throw new BusinessException("当前班次内未检测到设备【${equipmentCode}】的合格点检记录，请先完成点检。")
        }

        return ValidateRuleResult.success()
    }

    private String extractEquipmentCode(JSONObject jsonObject) {
        return jsonObject.getJSONArray("resources")
                .toJavaList(Map.class)
                .stream()
                .findFirst()
                .map { it.get("resourceCode")?.toString() }
                .orElse(null)
    }

    private static class ShiftInfo {
        String category
        String begin
        String end
    }

    private ShiftInfo getCurrentShift(Date now) {
        def sql = """
            SELECT t.category, t1.begin_time, t1.end_time
            FROM dwk_produce_classes t
            INNER JOIN dwk_produce_classes_time t1 ON t.id = t1.classes_id
            WHERE t.is_delete = false AND t1.is_delete = false
        """

        def nowTime = LocalTime.parse(new SimpleDateFormat("HH:mm").format(now))

        def shifts = basicGroovyService.findList(sql)
        for (row in shifts) {
            def begin = LocalTime.parse(row["begin_time"])
            def end = LocalTime.parse(row["end_time"])

            boolean inShift = begin.isBefore(end)
                    ? !nowTime.isBefore(begin) && nowTime.isBefore(end)
                    : !nowTime.isBefore(begin) || nowTime.isBefore(end)

            if (inShift) {
                return new ShiftInfo(
                        category: row["category"],
                        begin: row["begin_time"],
                        end: row["end_time"]
                )
            }
        }
        return null
    }

    private Map<String, String> getShiftDateTimeRange(Date now, String beginStr, String endStr) {
        def formatter = new SimpleDateFormat("yyyy-MM-dd")
        def today = formatter.format(now)
        def beginDateTime = "${today} ${beginStr}"
        def endDateTime = "${today} ${endStr}"

        if (LocalTime.parse(beginStr).isAfter(LocalTime.parse(endStr))) {
            // 跨天夜班，end+1天
            def cal = Calendar.getInstance()
            cal.setTime(now)
            cal.add(Calendar.DATE, 1)
            def tomorrow = formatter.format(cal.time)
            endDateTime = "${tomorrow} ${endStr}"
        }

        return [begin: beginDateTime, end: endDateTime]
    }
}
