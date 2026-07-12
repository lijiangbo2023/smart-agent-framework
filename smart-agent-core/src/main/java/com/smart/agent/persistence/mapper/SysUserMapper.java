package com.smart.agent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smart.agent.persistence.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for the SysUser entity.
 *
 * @description Provides CRUD operations on the sys_user table via MyBatis-Plus BaseMapper.
 * @author Jiangbo Li
 * @date 2025-07-12
 * @version 1.0
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
