package com.cjh.model.domain.request;


import lombok.Data;

import java.io.Serializable;

/*
* 序列化
* */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    String account;

    String password;

}
