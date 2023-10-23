# Protobuf Decompiler（Java版）
## 一、工具介绍
**功能：通过FileDescriptor（Java）反编译生成原始Proto文件**
1. 支持关键字
   - [x] syntax
   - [x] package
   - [x] option java_package
   - [x] option java_outer_classname
   - [x] option java_multiple_files
   - [x] enum
   - [x] message
   - [x] service
   - [x] rpc
2. 支持类型
    - [x] bool
    - [x] float
    - [x] double
    - [x] int32
    - [x] int64
    - [x] uint32
    - [x] uint64
    - [x] sint32
    - [x] sint64
    - [x] fixed32
    - [x] fixed64
    - [x] sfixed32
    - [x] sfixed64
    - [x] string
    - [x] bytes
3. 支持map
4. 支持oneof
5. 支持嵌套类型
   - [x] enum
   - [x] message
6. 支持字段修饰
   - [x] repeated
7. 支持字段选项
   - [x] deprecated
   - [x] packed
   - [x] lazy
8. 默认排除反编译"google/protobuf..."相关依赖proto
9. 支持自定义排除反编译文件，支持按依赖路径或者路径前缀排除
10. 支持语法示例：
```protobuf
syntax = "proto3";
package abc.xxx;

option java_package = "com.abc.protobuf.xxx";
option java_outer_classname = "XxxProto";
option java_multiple_files = true;

import "common/xxx.proto";

enum Enum1 {
    A1 = 0;
    B1 = 1;
}

enum Enum2 {
    A2 = 0;
    B2 = 1;
}

message Message1 {
    bool f01 = 1;
    float f02 = 2;
    double f03 = 3;
    int32 f04 = 4;
    int64 f05 = 5;
    uint32  f06 = 6;
    uint64 f07 = 7;
    sint32 f08 = 8;
    sint64 f09 = 9;
    fixed64 f10 = 10;
    fixed32 f11 = 11;
    sfixed32 f12 = 12;
    sfixed64  f13 = 13;
    string f14 = 14;
    bytes f15 = 15;
}

message Message2 {
    enum Enum00 { //嵌套enum
        A = 0;
        //...
    }
    message Message00 { //嵌套message
        bool f01 = 1;
        //...
    }
    string f01 = 1 [deprecated = true, packed = true, weak = true]; //字段选项
    Enum00 f02 = 2;
    Message00 f03 = 3;
    map<string, string> f04 = 4;
    oneof fn01 {
        string ff05 = 5;
        string ff06 = 6;
    }
}

service XxxService {
    rpc Xmethod (Message1) returns (Message2);
}
```
## 二、使用说明
1. 依赖反编译工具包
```xml
<dependency>
    <groupId>com.myth</groupId>
    <artifactId>protobuf-decompiler</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```
2. 反编译生成proto
```java
public static void decompile() throws IOException {
    FileDescriptor descriptor = XxxProto.getDescriptor(); //FileDescriptor
    String outDir = "/xxx/proto/"; //输出目录
    Set<String> excludeDependencies = Set.of(
            "xxx/xxx", //import前缀
            "xxx/a.proto", //import文件
            "b.proto" //import文件
    ); //自定义排除反编译文件，排除基础/通用依赖，例如：common/xxx.proto、google/protobuf/xxx
    ProtobufDecompiler.decompile(descriptor, outDir, excludeDependencies);
}
```
3. 项目中引入反编译生产的proto文件
4. 编译&使用
```shell
mvn clean compile
```