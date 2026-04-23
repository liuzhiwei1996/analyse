package org.analyse.analysestock.config.enu;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排序方向枚举（限定仅支持ASC/_DESC）
 */
@Getter
@AllArgsConstructor
public enum SortDirection {
    /** 升序 */
    ASC("asc"),
    /** 降序 */
    DESC("desc");

    // 枚举对应的前端传参/数据库排序字符串（小写，和前端统一）
    private final String value;

    /**
     * 字符串转枚举（前端传参解析，兼容大小写，如Asc/Desc也能解析）
     * @param value 前端传的排序字符串（asc/desc）
     * @return 排序枚举，无匹配则返回默认ASC
     */
    public static SortDirection fromValue(String value) {
        if (value == null || value.isBlank()) {
            return ASC; // 空值默认升序
        }
        for (SortDirection direction : values()) {
            if (direction.getValue().equalsIgnoreCase(value)) {
                return direction;
            }
        }
        return ASC; // 非法值默认升序，也可抛自定义异常（如SortParamInvalidException）
    }
}