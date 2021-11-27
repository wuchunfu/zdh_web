package com.zyc.zdh.controller;

import com.alibaba.fastjson.JSON;
import com.zyc.zdh.dao.EtlTaskUpdateLogsMapper;
import com.zyc.zdh.dao.ZdhNginxMapper;
import com.zyc.zdh.entity.*;
import com.zyc.zdh.job.SnowflakeIdWorker;
import com.zyc.zdh.service.DataSourcesService;
import com.zyc.zdh.service.EtlTaskService;
import com.zyc.zdh.shiro.RedisUtil;
import com.zyc.zdh.util.DBUtil;
import com.zyc.zdh.util.SFTPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
 * 单源ETL任务服务
 */
@Controller
public class ZdhRedisApiController extends BaseController{

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    RedisUtil redisUtil;

    @RequestMapping(value = "/redis/get/{key}", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String get_key(@PathVariable("key") String key) {
        if(redisUtil.exists(key)){
            return ReturnInfo.createInfo(RETURN_CODE.SUCCESS.getCode(),"查询成功", redisUtil.get(key));
        }
        return ReturnInfo.createInfo(RETURN_CODE.FAIL.getCode(),"查询失败", "没有找到key: "+key);
    }

    @RequestMapping(value = "/redis/del/{key}", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String del_key(@PathVariable("key") String key) {
        if(redisUtil.exists(key)){
            redisUtil.remove(key);
        }
        return ReturnInfo.createInfo(RETURN_CODE.SUCCESS.getCode(),"删除成功", null);
    }

    @RequestMapping(value = "/redis/keys", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String keys () {
        return ReturnInfo.createInfo(RETURN_CODE.SUCCESS.getCode(),"查询成功", redisUtil.keys());
    }

    @RequestMapping(value = "/redis/add/{key}", produces = "text/html;charset=UTF-8")
    @ResponseBody
    public String add_key(@PathVariable("key") String key, String value) {
        redisUtil.set(key,value);
        return ReturnInfo.createInfo(RETURN_CODE.SUCCESS.getCode(),"新增成功", null);
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
                    e.printStackTrace();
                }
                // 恢复访问控制权限
                fields[i].setAccessible(accessFlag);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            }
        }
    }

}