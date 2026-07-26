package io.github.ieshishinjin.splice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

/**
 * 自定义日志颜色：INFO 蓝、WARN 黄、ERROR 红。
 */
public class SpliceLogColor extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        return switch (event.getLevel().toInt()) {
            case Level.ERROR_INT -> "1;31";  // 红
            case Level.WARN_INT  -> "1;33";  // 黄
            case Level.INFO_INT  -> "1;34";  // 蓝
            default -> "0";
        };
    }
}
