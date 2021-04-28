/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dtstack.flinkx.connector.jdbc.converter;

import com.dtstack.flinkx.connector.jdbc.statement.FieldNamedPreparedStatement;
import com.dtstack.flinkx.converter.AbstractRowConverter;
import com.dtstack.flinkx.element.ColumnRowData;
import io.vertx.core.json.JsonArray;
import org.apache.flink.connector.jdbc.utils.JdbcTypeUtil;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;
import org.apache.flink.table.types.utils.TypeConversions;

import java.sql.ResultSet;

/** Base class for all converters that convert between JDBC object and Flink internal object. */
public abstract class AbstractJdbcRowConverter
        extends AbstractRowConverter<ResultSet, JsonArray, FieldNamedPreparedStatement> {

    public AbstractJdbcRowConverter(RowType rowType) {
        super(rowType);
    }

    public AbstractJdbcRowConverter() {
        super();
    }

    @Override
    public RowData toInternal(ResultSet resultSet) throws Exception {
        GenericRowData genericRowData = new GenericRowData(rowType.getFieldCount());
        for (int pos = 0; pos < rowType.getFieldCount(); pos++) {
            Object field = resultSet.getObject(pos + 1);
            genericRowData.setField(pos, toInternalConverters[pos].deserialize(field));
        }
        return genericRowData;
    }

    @Override
    public RowData toInternalLookup(JsonArray jsonArray) throws Exception {
        GenericRowData genericRowData = new GenericRowData(rowType.getFieldCount());
        for (int pos = 0; pos < rowType.getFieldCount(); pos++) {
            Object field = jsonArray.getValue(pos);
            genericRowData.setField(pos, toInternalConverters[pos].deserialize(field));
        }
        return genericRowData;
    }

    @Override
    public FieldNamedPreparedStatement toExternal(
            RowData rowData, FieldNamedPreparedStatement statement) throws Exception {
        for (int index = 0; index < rowData.getArity(); index++) {
            toExternalConverters[index].serialize(rowData, index, statement);
        }
        return statement;
    }

    /**
     * 从jdbc中读取数据的converter
     *
     * @param deserializationConverter
     * @param index
     * @return
     */
    protected DeserializationConverter<ResultSet> wrapIntoNullableInternalConverter(
            DeserializationConverter<ResultSet> deserializationConverter, int index) {
        return val -> {
            if (val.getObject(index) == null) {
                return null;
            } else {
                return deserializationConverter.deserialize(val);
            }
        };
    }

    /**
     * 往jdbc中写入数据的converter
     *
     * @param serializationConverter
     * @return
     */
    protected SerializationConverter<FieldNamedPreparedStatement> wrapIntoNullableExternalConverter(
            SerializationConverter serializationConverter) {
        return (val, index, statement) -> {
            if (((ColumnRowData) val).getField(index) == null) {
                statement.setObject(index, null);
            } else {
                serializationConverter.serialize(val, index, statement);
            }
        };
    }

    @Override
    protected SerializationConverter<FieldNamedPreparedStatement> wrapIntoNullableExternalConverter(
            SerializationConverter serializationConverter, LogicalType type) {
        final int sqlType =
                JdbcTypeUtil.typeInformationToSqlType(
                        TypeConversions.fromDataTypeToLegacyInfo(
                                TypeConversions.fromLogicalToDataType(type)));
        return (val, index, statement) -> {
            if (val == null
                    || val.isNullAt(index)
                    || LogicalTypeRoot.NULL.equals(type.getTypeRoot())) {
                statement.setNull(index, sqlType);
            } else {
                serializationConverter.serialize(val, index, statement);
            }
        };
    }
}
