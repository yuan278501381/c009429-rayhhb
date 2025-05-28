package groovy.aps

import com.alibaba.fastjson.JSON
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.aps.domain.dto.ApsDto
import com.tengnat.dwork.modules.script.abstracts.ApsNodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors

/**
 * 迈金搬过来的，磊哥写的
 *
 * 工厂日历排产
 * 1. 从最早的计划开始时间执行日历
 * 2. 若休息日为计划开始时间，则计划开始时间与结束时间都顺延+n
 * 3. 若休息日在计划开始日期-计划结束时间之间，则计划结束时间+n
 * 4. 若休息日为计划结束时间，则计划结束时间顺延+n
 */
class ApsFactoryCalendar extends ApsNodeGroovyClass {

    def basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    Map<String, Date> resourceLastEndMap = new HashMap<>(); // key: resourceCode

    @Override
    List<ApsDto> runScript(List<ApsDto> nodeData) {
        return handleScript(nodeData)
    }

    private List<ApsDto> handleScript(List<ApsDto> nodeData) {

        // 获取工厂日历
        List<BmfObject> factoryCalendars = basicGroovyService.find("produceCalendarRestDay", new HashMap<String, Object>())
        // 根据年份和月份分组
        Map<String, List<BmfObject>> factoryCalendarMap = factoryCalendars.stream().collect(Collectors.groupingBy(item -> item.getString("year") + "-" + item.getString("month"), Collectors.collectingAndThen(Collectors.toList(), list -> {
            list.stream().forEach { item ->
                if (item.getString("days") != null) {
                    List<String> days = JSON.parseObject(item.getString("days"), List.class)
                    item.put("days", days)
                }
            }
            return list
        })))


        for (final def node in nodeData) {
            Date earliestStartTime = null
            Date lastEndTime = null
            Date lastTaskEnd = null
            Date originalPlanEndTime = node.getPlanEndTime()
            int accumulatedDelay = 0 // 累计延迟天数
            List<ApsDto.Processe> processes = node.getProcesses()
            processes.stream().forEach(process -> {
                process.getProduceTasks().stream().forEach(task -> {
                    String resourceCode = task.getResourceCode();
                    // 获取该资源的上次结束时间
                    Date lastEndForResource = resourceLastEndMap.getOrDefault(resourceCode, null);
                    // 应用累计延迟到基准时间
                    Date originalStart = task.planStartTime
                    Date originalEnd = task.planEndTime

                    // 转换为LocalDate进行运算
                    LocalDate baseStart = toLocalDate(originalStart).plusDays(accumulatedDelay)
                    LocalDate baseEnd = toLocalDate(originalEnd).plusDays(accumulatedDelay)

                    // 修改2：仅同资源任务需要顺序性约束
                    if (lastEndForResource != null) {
                        LocalDate minStartDate = toLocalDate(lastEndForResource); // 去掉+1天的强制间隔
                        if (baseStart.isBefore(minStartDate)) {
                            int adjustDays = (int) ChronoUnit.DAYS.between(baseStart, minStartDate);
                            accumulatedDelay += adjustDays;
                            baseStart = baseStart.plusDays(adjustDays);
                            baseEnd = baseEnd.plusDays(adjustDays);
                        }
                    }
                    // 计算实际计划时间
                    PlanDate planDate = computeTaskPlan(factoryCalendarMap, baseStart, baseEnd);

                    // 修改3：更新该资源的最后结束时间
                    resourceLastEndMap.put(resourceCode, planDate.getEndDate());
//                    // 处理顺序性约束
//                    if (lastTaskEnd != null) {
//                        LocalDate minStartDate = toLocalDate(lastTaskEnd).plusDays(1)
//                        if (baseStart.isBefore(minStartDate)) {
//                            // 需要追加延迟保证顺序性
//                            int adjustDays = (int) ChronoUnit.DAYS.between(baseStart, minStartDate)
//                            accumulatedDelay += adjustDays
//                            baseStart = baseStart.plusDays(adjustDays)
//                            baseEnd = baseEnd.plusDays(adjustDays)
//                        }
//                    }
//
//                    // 计算本任务实际时间
//                    PlanDate planDate = computeTaskPlan(factoryCalendarMap, baseStart, baseEnd)

                    // 更新累计延迟（总延迟 = 基础延迟 + 新产生的延迟）
                    int newDelay = (int) ChronoUnit.DAYS.between(
                            toLocalDate(originalStart),
                            planDate.startDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    )
                    accumulatedDelay += newDelay

                    // 更新任务时间
                    task.planStartTime = planDate.startDate
                    task.planEndTime = planDate.endDate
                    if (earliestStartTime == null) {
                        earliestStartTime = planDate.getStartDate()
                    } else {
                        if (planDate.getStartDate().before(earliestStartTime)) {
                            earliestStartTime = planDate.getStartDate()
                        }
                    }
                    if (lastEndTime == null) {
                        lastEndTime = planDate.getEndDate()
                    } else {
                        if (planDate.getEndDate().after(lastEndTime)) {
                            lastEndTime = planDate.getEndDate()
                        }
                    }
                    lastTaskEnd = planDate.endDate
                })
                process.setPlanStartTime(earliestStartTime)
                process.setPlanEndTime(lastEndTime)
                // 延期判断
                if (lastEndTime != null && lastEndTime > originalPlanEndTime) {
                    node.setPlanEndTime(lastEndTime)
                    node.setFlag(true)
                } else {
                    node.setPlanEndTime(originalPlanEndTime)
                    node.setFlag(false)
                }
                // 更新工序时间（原有逻辑）
//                updateProcessTime(process)
            })
        }
        return nodeData
    }

    private PlanDate computeTaskPlan(Map<String, List<BmfObject>> calendarMap, LocalDate start, LocalDate end) {
        // 推迟开始日期
        int startDelay = calculateStartDelay(calendarMap, start)
        LocalDate adjustedStart = start.plusDays(startDelay)
        LocalDate adjustedEnd = end.plusDays(startDelay)

        // 计算执行期间假期
        int execHolidays = countHolidays(calendarMap, adjustedStart, adjustedEnd)
        LocalDate finalEnd = adjustedEnd.plusDays(execHolidays)

        return new PlanDate(
                startDate: Date.from(adjustedStart.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                endDate: Date.from(finalEnd.atStartOfDay(ZoneId.systemDefault()).toInstant())
        )
    }

    private int calculateStartDelay(Map<String, List<BmfObject>> calendarMap, LocalDate date) {
        int delay = 0
        while (isHoliday(calendarMap, date.plusDays(delay))) {
            delay++
            if (delay > 365) throw new RuntimeException("Excessive delay days")
        }
        delay
    }

    private int countHolidays(Map<String, List<BmfObject>> calendarMap, LocalDate start, LocalDate end) {
        int count = 0
        LocalDate current = start
        while (!current.isAfter(end)) {
            if (isHoliday(calendarMap, current)) count++
            current = current.plusDays(1)
        }
        count
    }

    private boolean isHoliday(Map<String, List<BmfObject>> calendarMap, LocalDate date) {
        String monthKey = "${date.year}-${String.format("%02d", date.monthValue)}"
        calendarMap.getOrDefault(monthKey, []).any { calendar ->
            List<String> crossDays = (List<String>) calendar.get("days")
            crossDays.contains(date.dayOfMonth.toString())
        }
    }

    LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date) {
            return new java.util.Date(date.getTime())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    static class PlanDate {
        Date startDate
        Date endDate

        void setStartDate(Date startDate) {
            this.startDate = startDate
        }

        void setEndDate(Date endDate) {
            this.endDate = endDate
        }

        Date getStartDate() {
            return startDate
        }

        Date getEndDate() {
            return endDate
        }
    }

}
