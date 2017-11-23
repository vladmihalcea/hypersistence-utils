package com.vladmihalcea.hibernate.type.json.internal;


interface JsonSerializer<T> {//TODO: the name of this interface can be inappropriate at a first look but the first step to support user defined ObjectMapper

    T fromString(String string);

}
