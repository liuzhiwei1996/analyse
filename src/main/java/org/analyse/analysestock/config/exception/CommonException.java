package org.analyse.analysestock.config.exception;


import org.analyse.analysestock.config.enu.CodeMsg;

/**
 * @author dengzhiqiang
 * @date 2021/11/25
 * @describe
 */
public class CommonException extends RuntimeException{

    private int code;

    public CommonException(int code, String msg){
        super(msg);
        this.code = code;
    }

    public CommonException(int code){
        super(CodeMsg.getName(code));
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}