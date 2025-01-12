package com.zyc.zdh.job;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.zyc.queue.Producer;
import com.zyc.queue.client.ConsumerHandlerImpl;
import com.zyc.queue.client.ProducerImpl;
import com.zyc.zdh.dao.StrategyGroupInstanceMapper;
import com.zyc.zdh.dao.StrategyInstanceMapper;
import com.zyc.zdh.dao.TaskGroupLogInstanceMapper;
import com.zyc.zdh.dao.TaskLogInstanceMapper;
import com.zyc.zdh.entity.*;
import com.zyc.zdh.monitor.Sys;
import com.zyc.zdh.shiro.RedisUtil;
import com.zyc.zdh.util.DAG;
import com.zyc.zdh.util.DateUtil;
import com.zyc.zdh.util.HttpUtil;
import com.zyc.zdh.util.SpringContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.shiro.session.mgt.SimpleSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;

/**
 * 检查策略及策略组,判定上下游依赖
 */
public class CheckStrategyDepJob implements CheckDepJobInterface{

    private final static String task_log_status="etl";
    private static Logger logger = LoggerFactory.getLogger(CheckStrategyDepJob.class);

    public static List<ZdhDownloadInfo> zdhDownloadInfos = new ArrayList<>();

    @Override
    public void setObject(Object o) {

    }

    public void run() {
        try {
            MDC.put("logId", UUID.randomUUID().toString());
            logger.debug("开始检测策略组任务...");
            StrategyGroupInstanceMapper sgim=(StrategyGroupInstanceMapper) SpringContext.getBean("strategyGroupInstanceMapper");
            StrategyInstanceMapper sim=(StrategyInstanceMapper) SpringContext.getBean("strategyInstanceMapper");


            // 如果当前任务组无子任务则直接设置完成,或者是在线任务组状态更新成执行中
            List<StrategyGroupInstance> non_group=sgim.selectTaskGroupByStatus(new String[]{JobStatus.SUB_TASK_DISPATCH.getValue(),JobStatus.KILL.getValue()});
            for(StrategyGroupInstance group :non_group){
                List<StrategyInstance> sub_task=sim.selectByGroupInstanceId(group.getId(), null);
                if(sub_task == null || sub_task.size() < 1){
                    if(group.getStatus().trim().equalsIgnoreCase(JobStatus.KILL.getValue())){
                        group.setStatus(JobStatus.KILLED.getValue());
                    }else{
                        group.setStatus(JobStatus.FINISH.getValue());
                    }
                    //group.setProcess("100");
                    JobDigitalMarket.updateTaskLog(group,sgim);
                    logger.info("当前策略组没有子任务可执行,当前任务组设为完成");
                    JobDigitalMarket.insertLog(group,"INFO","当前策略组没有子任务可执行,当前策略组设为完成");
                }
            }

            //获取可执行的任务组
            List<StrategyGroupInstance> sgis=sgim.selectTaskGroupByStatus(new String[]{JobStatus.CHECK_DEP.getValue(),JobStatus.CREATE.getValue()});
            // 此处可做任务并发限制,当前未限制并发
            for(StrategyGroupInstance sgi :sgis){
                String tmp_status=sgim.selectByPrimaryKey(sgi.getId()).getStatus();
                if( !tmp_status.equalsIgnoreCase("kill") && !tmp_status.equalsIgnoreCase("killed") ){
                    //在检查依赖时杀死任务--则不修改状态
                    updateTaskGroupLogInstanceStatus(sgi);
                }
            }

            //检查子任务是否可以运行
            run_sub_task();
            //检测任务组是否已经完成
            create_group_final_status();
        } catch (Exception e) {
             logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
             MDC.remove("logId");
        }

    }

    /**
     * 修改组任务状态及子任务状态
     * @param sgi
     */
    public static void updateTaskGroupLogInstanceStatus(StrategyGroupInstance sgi){
        StrategyGroupInstanceMapper sgim=(StrategyGroupInstanceMapper) SpringContext.getBean("strategyGroupInstanceMapper");
        StrategyInstanceMapper sim=(StrategyInstanceMapper) SpringContext.getBean("strategyInstanceMapper");
        sgi.setStatus(JobStatus.SUB_TASK_DISPATCH.getValue());
        //sgi.setProcess("7.5");
        //sgi.setServer_id(JobCommon2.web_application_id);//重新设置调度器标识,retry任务会定期检查标识是否有效,对于组任务只有只有CREATE 状态检查此标识才有用
        //更新任务依赖时间
        //process_time_info pti=tgli.getProcess_time2();
        //pti.setCheck_dep_time(DateUtil.getCurrentTime());
        //tgli.setProcess_time(pti);

        JobDigitalMarket.updateTaskLog(sgi,sgim);
        debugInfo(sgi);
    }

    /**
     * 检查子任务是否可以运行
     */
    public static void run_sub_task() {
        try {
            logger.debug("开始检测子任务依赖...");
            StrategyGroupInstanceMapper sgim=(StrategyGroupInstanceMapper) SpringContext.getBean("strategyGroupInstanceMapper");
            StrategyInstanceMapper sim=(StrategyInstanceMapper) SpringContext.getBean("strategyInstanceMapper");

            //获取所有实时类型的可执行子任务
            List<StrategyInstance> strategyInstanceOnLineList=sim.selectThreadByStatus1(new String[] {JobStatus.CREATE.getValue(),JobStatus.CHECK_DEP.getValue()}, "online");
            for(StrategyInstance tl :strategyInstanceOnLineList){
                tl.setStatus(JobStatus.ETL.getValue());
                tl.setUpdate_time(new Timestamp(new Date().getTime()));
                JobDigitalMarket.updateTaskLog(tl,sim);
            }

            //获取所有离线类型的可执行的非依赖类型子任务
            List<StrategyInstance> strategyInstanceList=sim.selectThreadByStatus1(new String[] {JobStatus.CREATE.getValue(),JobStatus.CHECK_DEP.getValue()}, "offline");
            for(StrategyInstance tl :strategyInstanceList){
                try{
                    //如果skip状态,跳过当前策略实例
                    if(tl.getStatus().equalsIgnoreCase(JobStatus.SKIP.getValue())){
                        continue;
                    }
                    //如果上游任务kill,killed 设置本实例为killed
                    String pre_tasks=tl.getPre_tasks();
                    if(!StringUtils.isEmpty(pre_tasks)){
                        String[] task_ids=pre_tasks.split(",");
                        List<StrategyInstance> tlis=sim.selectByIds(task_ids);

                        int level= Integer.valueOf(tl.getDepend_level());
                        if(tlis!=null && tlis.size()>0 && level==0){
                            // 此处判定级别0：成功时运行,1:杀死时运行,2:失败时运行,默认成功时运行
                            tl.setStatus(JobStatus.KILLED.getValue());
                            JobDigitalMarket.updateTaskLog(tl,sim);
                            JobDigitalMarket.insertLog(tl,"INFO","检测到上游任务:"+tlis.get(0).getId()+",失败或者已被杀死,更新本任务状态为killed");
                            continue;
                        }
                        if(level >= 1){
                            // 此处判定级别0：成功时运行,1:杀死时运行,2:失败时运行,默认成功时运行
                            //杀死触发,如果所有上游任务都以完成
                            List<StrategyInstance> tlis_finish= sim.selectByFinishIds(task_ids);
                            if(tlis_finish.size()==task_ids.length){
                                tl.setStatus(JobStatus.SKIP.getValue());
                                JobDigitalMarket.updateTaskLog(tl, sim);
                                //JobCommon2.updateTaskLog(tl,taskLogInstanceMapper);
                                JobDigitalMarket.insertLog(tl,"INFO","检测到上游任务:"+pre_tasks+",都以完成或者跳过,更新本任务状态为SKIP");
                                continue;
                            }
                        }
                    }

                    //根据dag判断是否对当前任务进行
                    DAG dag=new DAG();
                    String group_instance_id=tl.getGroup_instance_id();
                    List<StrategyInstance> strategyInstanceList2=sim.selectByGroupInstanceId(group_instance_id, null);
                    Map<String,StrategyInstance> dagStrategyInstance=new HashMap<>();
                    //此处必须使用group_instance_id实例id查询,因可能有策略实例已完成
                    for(StrategyInstance t2 :strategyInstanceList2){
                        if(t2.getGroup_instance_id().equalsIgnoreCase(group_instance_id)) {
                            dagStrategyInstance.put(t2.getId(), t2);
                            String pre_tasks2=t2.getPre_tasks();
                            if (!StringUtils.isEmpty(pre_tasks2)) {
                                String[] task_ids = pre_tasks2.split(",");
                                for (String instance_id:task_ids){
                                    dag.addEdge(instance_id, t2.getId());
                                }
                            }
                        }
                    }
                    Set<String> parents = dag.getAllParent(tl.getId());
                    if(parents==null || parents.size()==0){
                        //无父节点直接运行即可
                        System.out.println("根节点模拟发放任务--开始");
                        System.out.println("=======================");
                        System.out.println(JSON.toJSONString(tl));
                        if(tl.getStatus().equalsIgnoreCase(JobStatus.SKIP.getValue())){
                            continue;
                        }
                        JobDigitalMarket.insertLog(tl,"INFO","当前策略任务:"+tl.getId()+",推送类型:"+tl.getTouch_type());
                        if(tl.getTouch_type()==null || !tl.getTouch_type().equalsIgnoreCase("queue")){
                            resovleStrategyInstance(tl);
                        }
                        System.out.println("根节点模拟发放任务--结束");
                        //更新任务状态为检查完成
                        tl.setStatus(JobStatus.CHECK_DEP_FINISH.getValue());
                        JobDigitalMarket.updateTaskLog(tl,sim);
                    }else{
                        boolean is_run=true;
                        for (String parent:parents){
                            if(!dagStrategyInstance.containsKey(parent)){
                                is_run=false;
                                System.out.println("未找到任务父节点信息");
                                break ;
                            }
                            if(!dagStrategyInstance.get(parent).getStatus().equalsIgnoreCase(JobStatus.SKIP.getValue()) &&
                                    !dagStrategyInstance.get(parent).getStatus().equalsIgnoreCase(JobStatus.FINISH.getValue())){
                                //当前不可执行
                                is_run=false;
                                //System.out.println(JSON.toJSONString(tl));
                                //System.out.println("当前任务父任务存在为完成");
                                break ;
                            }
                        }

                        if(is_run){
                            //上游都以完成,可执行,任务发完执行集群 此处建议使用优先级队列 todo
                            System.out.println("模拟发放任务--开始");
                            JobDigitalMarket.insertLog(tl,"INFO","当前策略任务:"+tl.getId()+",推送类型:"+tl.getTouch_type());
                            if(tl.getStatus().equalsIgnoreCase(JobStatus.SKIP.getValue())){
                                continue;
                            }
                            if(tl.getTouch_type()==null || !tl.getTouch_type().equalsIgnoreCase("queue")){
                                resovleStrategyInstance(tl);
                            }
                            System.out.println("=======================");
                            System.out.println(JSON.toJSONString(tl));
                            System.out.println("模拟发放任务--结束");

                            //更新任务状态为检查完成
                            tl.setStatus(JobStatus.CHECK_DEP_FINISH.getValue());
                            JobDigitalMarket.updateTaskLog(tl,sim);
                        }
                    }
                }catch (Exception e){
                    //任务未知异常,设置实例为失败
                    //更新任务状态为检查完成
                    tl.setStatus(JobStatus.ERROR.getValue());
                    JobDigitalMarket.updateTaskLog(tl,sim);
                    logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
                }
            }

        } catch (Exception e) {
             logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
        }

    }

    /**
     * 检测任务组是否已经完成,
     * 运行中+完成+失败=总数
     */
    public static void create_group_final_status(){
        StrategyGroupInstanceMapper sgim=(StrategyGroupInstanceMapper) SpringContext.getBean("strategyGroupInstanceMapper");
        StrategyInstanceMapper sim=(StrategyInstanceMapper) SpringContext.getBean("strategyInstanceMapper");
        List<StrategyGroupInstance> sgis=sgim.selectTaskGroupByStatus(new String[]{JobStatus.SUB_TASK_DISPATCH.getValue(),JobStatus.KILL.getValue()});

        for(StrategyGroupInstance sgi:sgis){
            //run_date 结构：run_date:[{task_log_instance_id,etl_task_id,etl_context,more_task}]
            //System.out.println(tgli.getRun_jsmind_data());
            if(StringUtils.isEmpty(sgi.getRun_jsmind_data())){
                continue;
            }
            JSONArray jary=JSON.parseObject(sgi.getRun_jsmind_data()).getJSONArray("run_data");
            List<String> tlidList=new ArrayList<>();
            for(Object obj:jary){
                String tlid=((JSONObject) obj).getString("strategy_instance_id");
                //System.out.println("task_log_instance_id:"+tlid);
                if(tlid!=null)
                  tlidList.add(tlid);
            }
            if (tlidList.size()<1)
                continue;

            List<task_num_info> lm=sim.selectStatusByIds(new String[]{sgi.getId()});
            int finish_num=0;
            int error_num=0;
            int kill_num=0;
            for(task_num_info tni:lm){
                if(tni.getStatus().equalsIgnoreCase(JobStatus.FINISH.getValue()) || tni.getStatus().equalsIgnoreCase(JobStatus.SKIP.getValue())){
                    finish_num=finish_num+tni.getNum();
                }
                if(tni.getStatus().equalsIgnoreCase(JobStatus.ERROR.getValue())){
                    error_num=tni.getNum();
                }
                if(tni.getStatus().equalsIgnoreCase(JobStatus.KILLED.getValue())){
                    kill_num=tni.getNum();
                }
            }

//            System.out.println("finish:"+finish_num);
//            System.out.println("kill_num:"+kill_num);
//            System.out.println("error_num:"+error_num);
            //如果 有运行状态，创建状态，杀死状态 则表示未运行完成
            //String process=((finish_num+error_num+kill_num)/tlidList.size())*100 > Double.valueOf(s.getProcess())? (((finish_num+error_num+kill_num)/tlidList.size())*100)+"":tgli.getProcess();
            //String msg="更新进度为:"+process;
            if(finish_num==tlidList.size()){
                //表示全部完成
                sgim.updateStatusById3(JobStatus.FINISH.getValue() ,DateUtil.getCurrentTime(),sgi.getId());
                //tglim.updateStatusById(JobStatus.FINISH.getValue(),tgli.getId());
                JobDigitalMarket.insertLog(sgi,"INFO","任务组已完成");
            }else if(kill_num==tlidList.size()){
                //表示组杀死
                sgim.updateStatusById3(JobStatus.KILLED.getValue() ,DateUtil.getCurrentTime(),sgi.getId());
               // tglim.updateStatusById(JobStatus.KILLED.getValue(),tgli.getId());
                JobDigitalMarket.insertLog(sgi,"INFO","任务组已杀死");
            }else if(finish_num+error_num == tlidList.size()){
                //存在失败
                sgim.updateStatusById3(JobStatus.ERROR.getValue() ,DateUtil.getCurrentTime(),sgi.getId());
                JobDigitalMarket.insertLog(sgi,"INFO","任务组以失败,具体信息请点击子任务查看");
            }else if(finish_num+error_num+kill_num == tlidList.size()){
                //存在杀死任务
                sgim.updateStatusById3(JobStatus.KILLED.getValue() ,DateUtil.getCurrentTime(),sgi.getId());
                JobDigitalMarket.insertLog(sgi,"INFO","任务组以完成,存在杀死任务,具体信息请点击子任务查看");
            }
        }

    }


    public static void resovleStrategyInstance(StrategyInstance strategyInstance) throws Exception {

        if(strategyInstance==null || StringUtils.isEmpty(strategyInstance.getInstance_type())){
            throw new Exception("策略实例及实例类型信息不可为空");
        }
        if(strategyInstance.getTouch_type()==null || !strategyInstance.getTouch_type().equalsIgnoreCase("queue")){

            return ;
        }

        ProducerImpl producer=getProduct();

        String priority = strategyInstance.getPriority();
        if(StringUtils.isEmpty(priority)){
            priority="0";
        }
        //标签任务,发往标签计算服务
        if(strategyInstance.getInstance_type().equalsIgnoreCase(StrategyInstanceType.LABEL.getCode())){
            send(producer, Integer.parseInt(priority), strategyInstance, StrategyInstanceType.LABEL.getCode());
        }

        producer.close();

    }

    public static ProducerImpl getProduct() throws Exception {
        try{
            Environment environment= (Environment) SpringContext.getBean("environment");
            ProducerImpl producer=new ProducerImpl();
            String host = environment.getProperty("queue.server.host","127.0.0.1");
            int port = Integer.parseInt(environment.getProperty("queue.server.port","9001"));
            producer.init(host, port);
            producer.setConsumerHandler(new ConsumerHandlerImpl());//已废弃,必须写,历史版本兼容使用
            if(!producer.is_connect(5)){
               throw new Exception("链接队列失败");
            }
            return producer;
        }catch (Exception e){
            logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
            throw e;
        }


    }

    public static void send(ProducerImpl producer,int priority, Object msg,String queue_name) throws Exception {
        producer.setQueue(queue_name);
        producer.send(msg, priority,5);
    }

    public static void debugInfo(Object obj) {
        Field[] fields = obj.getClass().getDeclaredFields();
        for (int i = 0, len = fields.length; i < len; i++) {
            // 对于每个属性，获取属性名
            String varName = fields[i].getName();
            try {
                // 获取原来的访问控制权限
                boolean accessFlag = fields[i].isAccessible();
                // 修改访问控制权限
                fields[i].setAccessible(true);
                // 获取在对象f中属性fields[i]对应的对象中的变量
                Object o;
                try {
                    o = fields[i].get(obj);
                    logger.info("传入的对象中包含一个如下的变量：" + varName + " = " + o);
                } catch (IllegalAccessException e) {
                    // TODO Auto-generated catch block
                     logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
                }
                // 恢复访问控制权限
                fields[i].setAccessible(accessFlag);
            } catch (IllegalArgumentException e) {
                 logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
            }
        }
    }

}
