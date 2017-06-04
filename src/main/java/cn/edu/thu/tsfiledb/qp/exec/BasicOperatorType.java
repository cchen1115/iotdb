package cn.edu.thu.tsfiledb.qp.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.edu.thu.tsfile.timeseries.filter.definition.FilterFactory;
import cn.edu.thu.tsfile.timeseries.filter.definition.SingleSeriesFilterExpression;
import cn.edu.thu.tsfile.timeseries.filter.definition.filterseries.FilterSeries;
import cn.edu.thu.tsfiledb.qp.constant.SQLConstant;
import cn.edu.thu.tsfiledb.qp.exception.logical.operator.BasicOperatorException;

/**
 * all basic operator in filter
 * 
 * @author kangrong
 *
 */
public enum BasicOperatorType {
    EQ {
        @Override
        public <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
                C column, T value) {
            return FilterFactory.eq(column, value);
        }
    },
    LTEQ {
        @Override
        public <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
                C column, T value) {
            return FilterFactory.ltEq(column, value, true);
        }
    },
    LT {
        @Override
        public <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
                C column, T value) {
            return FilterFactory.ltEq(column, value, false);
        }
    },
    GTEQ {
        @Override
        public <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
                C column, T value) {
            return FilterFactory.gtEq(column, value, true);
        }
    },
    GT {
        @Override
        public <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
                C column, T value) {
            return FilterFactory.gtEq(column, value, false);
        }
    },
    NOTEQUAL {
        @Override
        public <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
                C column, T value) {
            return FilterFactory.noteq(column, value);
        }
    };
    private static Logger LOG = LoggerFactory.getLogger(BasicOperatorType.class);

    public static BasicOperatorType getBasicOpBySymbol(int tokenIntType)
            throws BasicOperatorException {
        switch (tokenIntType) {
            case SQLConstant.EQUAL:
                return EQ;
            case SQLConstant.LESSTHANOREQUALTO:
                return LTEQ;
            case SQLConstant.LESSTHAN:
                return LT;
            case SQLConstant.GREATERTHANOREQUALTO:
                return GTEQ;
            case SQLConstant.GREATERTHAN:
                return GT;
            case SQLConstant.NOTEQUAL:
                return NOTEQUAL;
            default:
                throw new BasicOperatorException("unsupported type:{}"
                        + SQLConstant.tokenNames.get(tokenIntType));
        }
    }

    public abstract <T extends Comparable<T>, C extends FilterSeries<T>> SingleSeriesFilterExpression getSingleSeriesFilterExpression(
            C column, T value);
}
