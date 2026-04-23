package org.analyse.analysestock.config.exception;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.analyse.analysestock.config.util.HttpPost;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;

/**
 * @Author zhuwencai
 * @Date 2020/4/22 12:51
 */
@ControllerAdvice
@Slf4j
@Component
public class GlobalExceptionHandler {
    /**
     * 如果发现新确定的类型的异常，但是这种异常又不需要人工去修复和改正的，可以把他捕捉然后参考 异常拦截2,3,4 的实现方式进行处理
     *
     * @param request
     * @param e
     * @return
     */
    @ExceptionHandler(value = Exception.class)
    @ResponseBody
    public ResultData<CommonException> errorHander(HttpServletRequest request, Exception e) {

        log.warn("请求地址 :{},方式:{},IP :{}, 参数 : {} URL信息:{}", request.getRequestURL(), request.getMethod(), HttpPost.getIpAddress(request), "", JSON.toJSONString(request.getParameterMap()));
        //自己抛出的异常
        if (e instanceof CommonException) {
            CommonException commonException = (CommonException) e;
            log.warn("异常拦截1：{}", e.getMessage());
            return ResultUtil.error(commonException);
            //方法参数无效的异常
        } else if (e instanceof MethodArgumentNotValidException) {
            List<ObjectError> allErrors = ((MethodArgumentNotValidException) e).getBindingResult().getAllErrors();
            StringBuilder message = new StringBuilder();
            for (ObjectError objectError : allErrors) {
                message.append(objectError.getDefaultMessage());
            }
            log.warn("异常拦截2：{}", e.getMessage());
            return ResultUtil.error(-1, String.valueOf(message));
            //缺少请求参数的异常
        } else if (e instanceof MissingServletRequestParameterException) {
            log.warn("异常拦截3：{}", e.getMessage());
            return ResultUtil.error(-1, e.getMessage());
            //请求方法是不支持的类型，比如不是json和utf_8的模式
        } else if (e instanceof HttpMediaTypeException) {
            log.warn("异常拦截4：{},{}", e.getMessage(), e.getClass());
            return ResultUtil.error(-1, "参数类型异常");
        } else if (e instanceof HttpMessageNotReadableException) {
            log.warn("异常拦截4_1：{},{}", e.getMessage(), e.getClass());
            return ResultUtil.error(-1, "请求参数格式错误");
            //参数类型异常
        } else if (e instanceof IllegalArgumentException) {
            log.warn("异常拦截5：{},{}", e.getMessage(), e.getClass());
            return ResultUtil.error(-1, e.getMessage());
            //请求方法不是支持的
        } else if (e instanceof HttpRequestMethodNotSupportedException) {
            log.warn("异常拦截6：{},{}", e.getMessage(), e.getClass());
            return ResultUtil.error(-1, "请求方法不是支持的");
            //sql异常
        } else if (e instanceof SQLException) {
            log.error("异常拦截7：{},{}", e.getMessage(), e.getClass());
            return ResultUtil.error(-1, "SQL异常");
            //未知的异常，需要处理的
        } else {
            log.error("异常拦截：{}", e.getMessage(), e);
            return ResultUtil.error(-1, e.getMessage() == null ? "服务开小差了，请稍后重试" : e.getMessage());
        }
    }
}
