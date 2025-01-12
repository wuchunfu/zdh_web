package com.zyc.zdh.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zyc.zdh.annotation.White;
import com.zyc.zdh.dao.*;
import com.zyc.zdh.entity.*;
import com.zyc.zdh.job.SnowflakeIdWorker;
import com.zyc.zdh.service.EtlTaskService;
import com.zyc.zdh.util.Const;
import com.zyc.zdh.util.DBUtil;
import com.zyc.zdh.util.DateUtil;
import com.zyc.zdh.util.SFTPUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import tk.mybatis.mapper.entity.Example;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * datax ETL任务服务
 */
@Controller
public class ZdhEtlDataxAutoController extends BaseController{

    public Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    DataSourcesMapper dataSourcesMapper;
    @Autowired
    EtlTaskDataxAutoMapper etlTaskDataxAutoMapper;


    /**
     * 单源ETL首页
     * @return
     */
    @RequestMapping("/etl_task_datax_auto_index")
    public String etl_task_datax_auto_index() {

        return "etl/etl_task_datax_auto_index";
    }

    /**
     * 获取单源ETL任务明细
     * @param id 主键ID
     * @return
     */
    @RequestMapping(value = "/etl_task_datax_auto_detail", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ReturnInfo<EtlTaskDataxAutoInfo> etl_task_datax_auto_detail(String id) {
        try{
            EtlTaskDataxAutoInfo eti=etlTaskDataxAutoMapper.selectByPrimaryKey(id);
            return ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(), "查询成功", eti);
        }catch (Exception e){
            return ReturnInfo.build(RETURN_CODE.FAIL.getCode(), "查询失败", e);
        }

    }

    /**
     * 根据条件模糊查询单源ETL任务信息
     * @param etl_context 关键字
     * @param file_name 数据源关键字
     * @return
     */
    @RequestMapping(value = "/etl_task_datax_auto_list", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ReturnInfo<PageResult<List<EtlTaskDataxAutoInfo>>> etl_task_datax_auto_list(String etl_context, String file_name,int limit, int offset) {
        try{
            EtlTaskDataxAutoInfo etlTaskDataxAutoInfo = new EtlTaskDataxAutoInfo();
            Example example = new Example(etlTaskDataxAutoInfo.getClass());
            List<EtlTaskDataxAutoInfo> etlTaskDataxAutoInfos = new ArrayList<>();
            Example.Criteria cri = example.createCriteria();
            cri.andEqualTo("owner", getOwner());
            cri.andEqualTo("is_delete", Const.NOT_DELETE);
            if (!StringUtils.isEmpty(etl_context)) {
                Example.Criteria cri2 = example.and();
                cri2.andLike("etl_context", getLikeCondition(etl_context));
            }
            example.setOrderByClause("update_time desc");
            RowBounds rowBounds=new RowBounds(offset,limit);
            int total = etlTaskDataxAutoMapper.selectCountByExample(example);

            etlTaskDataxAutoInfos = etlTaskDataxAutoMapper.selectByExampleAndRowBounds(example, rowBounds);

            PageResult<List<EtlTaskDataxAutoInfo>> pageResult=new PageResult<>();
            pageResult.setTotal(total);
            pageResult.setRows(etlTaskDataxAutoInfos);

            return ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(), "查询成功", pageResult);
        }catch (Exception e){
            String error = "类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}";
            logger.error(error, e);
            return  ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(), "查询成功", e);
        }

    }


    /**
     * 根据条件模糊查询单源ETL任务信息
     * @return
     */
    @RequestMapping(value = "/etl_task_datax_auto_all_list", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    @White
    public ReturnInfo<List<EtlTaskDataxAutoInfo>> etl_task_datax_auto_all_list() {
        try{
            EtlTaskDataxAutoInfo etlTaskDataxAutoInfo = new EtlTaskDataxAutoInfo();
            Example example = new Example(etlTaskDataxAutoInfo.getClass());
            List<EtlTaskDataxAutoInfo> etlTaskDataxAutoInfos = new ArrayList<>();
            Example.Criteria cri = example.createCriteria();
            cri.andEqualTo("owner", getOwner());
            cri.andEqualTo("is_delete", Const.NOT_DELETE);

            example.setOrderByClause("update_time desc");

            etlTaskDataxAutoInfos = etlTaskDataxAutoMapper.selectByExample(example);


            return ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(), "查询成功", etlTaskDataxAutoInfos);
        }catch (Exception e){
            String error = "类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}";
            logger.error(error, e);
            return  ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(), "查询成功", e);
        }

    }


    /**
     * 批量删除单源ETL任务
     * @param ids id数组
     * @return
     */
    @RequestMapping(value = "/etl_task_datax_auto_delete", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    @Transactional(propagation= Propagation.NESTED)
    public ReturnInfo etl_task_datax_auto_delete(String[] ids) {
        try{
            etlTaskDataxAutoMapper.deleteLogicByIds("etl_task_datax_auto_info", ids, new Timestamp(new Date().getTime()));
            return ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(),RETURN_CODE.SUCCESS.getDesc(), null);
        }catch (Exception e){
            String error = "类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}";
			logger.error(error, e);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return ReturnInfo.build(RETURN_CODE.FAIL.getCode(),e.getMessage(), null);
        }
    }

    /**
     * 新增单源ETL任务首页
     * @return
     */
    @RequestMapping("/etl_task_datax_auto_add_index")
    public String etl_task_datax_auto_add() {

        return "etl/etl_task_datax_auto_add_index";
    }


    /**
     * 新增单源ETL任务
     * 如果输入数据源类型是外部上传,会补充文件服务器信息
     * @param etlTaskDataxAutoInfo
     * @return
     */
    @RequestMapping(value="/etl_task_datax_auto_add", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    @Transactional(propagation= Propagation.NESTED)
    public ReturnInfo etl_task_datax_auto_add(EtlTaskDataxAutoInfo etlTaskDataxAutoInfo) {
        //String json_str=JSON.toJSONString(request.getParameterMap());
        try{
            etlTaskDataxAutoInfo.setId(SnowflakeIdWorker.getInstance().nextId()+"");
            etlTaskDataxAutoInfo.setOwner(getOwner());
            etlTaskDataxAutoInfo.setCreate_time(new Timestamp(new Date().getTime()));
            etlTaskDataxAutoInfo.setUpdate_time(new Timestamp(new Date().getTime()));
            etlTaskDataxAutoInfo.setIs_delete(Const.NOT_DELETE);

            etlTaskDataxAutoMapper.insert(etlTaskDataxAutoInfo);
            return ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(),RETURN_CODE.SUCCESS.getDesc(), null);
        }catch (Exception e){
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            String error = "类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}";
            logger.error(error, e);
            return ReturnInfo.build(RETURN_CODE.FAIL.getCode(),e.getMessage(), null);
        }
    }


    /**
     * 单源ETL任务更新
     * todo 此次是否每次都更新文件服务器信息,待优化
     * @param etlTaskDataxAutoInfo
     * @return
     */
    @RequestMapping(value="/etl_task_datax_auto_update", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    @ResponseBody
    @Transactional(propagation= Propagation.NESTED)
    public ReturnInfo etl_task_datax_auto_update(EtlTaskDataxAutoInfo etlTaskDataxAutoInfo) {
        try{

            EtlTaskDataxAutoInfo oldEtlTaskDataxAutoInfo= etlTaskDataxAutoMapper.selectByPrimaryKey(etlTaskDataxAutoInfo.getId());

            etlTaskDataxAutoInfo.setOwner(oldEtlTaskDataxAutoInfo.getOwner());
            etlTaskDataxAutoInfo.setCreate_time(oldEtlTaskDataxAutoInfo.getCreate_time());
            etlTaskDataxAutoInfo.setUpdate_time(new Timestamp(new Date().getTime()));
            etlTaskDataxAutoInfo.setIs_delete(Const.NOT_DELETE);
            etlTaskDataxAutoMapper.updateByPrimaryKey(etlTaskDataxAutoInfo);

            return ReturnInfo.build(RETURN_CODE.SUCCESS.getCode(),RETURN_CODE.SUCCESS.getDesc(), null);
        }catch (Exception e){
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            String error = "类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}";
			logger.error(error, e);
            return ReturnInfo.build(RETURN_CODE.FAIL.getCode(),e.getMessage(), null);
        }
    }


    private void debugInfo(Object obj) {
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
                    System.err.println("传入的对象中包含一个如下的变量：" + varName + " = " + o);
                } catch (IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    String error = "类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}";
                    logger.error(error, e);
                }
                // 恢复访问控制权限
                fields[i].setAccessible(accessFlag);
            } catch (IllegalArgumentException e) {
                 logger.error("类:"+Thread.currentThread().getStackTrace()[1].getClassName()+" 函数:"+Thread.currentThread().getStackTrace()[1].getMethodName()+ " 异常: {}", e);
            }
        }
    }

}
