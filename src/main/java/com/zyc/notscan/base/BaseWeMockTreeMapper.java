package com.zyc.notscan.base;

import com.zyc.notscan.BaseMapper;
import com.zyc.zdh.entity.WeMockTreeInfo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.type.JdbcType;

public interface BaseWeMockTreeMapper<T> extends BaseMapper<T> {
}