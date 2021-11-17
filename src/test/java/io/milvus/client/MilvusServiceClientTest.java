/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.milvus.client;

import io.milvus.exception.ParamException;
import io.milvus.grpc.*;
import io.milvus.param.*;
import io.milvus.param.alias.AlterAliasParam;
import io.milvus.param.alias.CreateAliasParam;
import io.milvus.param.alias.DropAliasParam;
import io.milvus.param.collection.*;
import io.milvus.param.control.GetMetricsParam;
import io.milvus.param.control.GetPersistentSegmentInfoParam;
import io.milvus.param.control.GetQuerySegmentInfoParam;
import io.milvus.param.dml.*;
import io.milvus.param.index.*;
import io.milvus.param.partition.*;
import io.milvus.server.MockMilvusServer;
import io.milvus.server.MockMilvusServerImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MilvusServiceClientTest {
    private final int testPort = 53019;
    private MockMilvusServerImpl mockServerImpl;

    private MockMilvusServer startServer() {
        mockServerImpl = new MockMilvusServerImpl();
        MockMilvusServer mockServer = new MockMilvusServer(testPort, mockServerImpl);
        mockServer.start();
        return mockServer;
    }

    private MilvusServiceClient startClient() {
        String testHost = "localhost";
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(testHost)
                .withPort(testPort)
                .build();
        return new MilvusServiceClient(connectParam);
    }

    @SuppressWarnings("unchecked")
    private <T, P> void testFuncByName(String funcName, T param) {
        try {
            Class<?> clientClass = MilvusServiceClient.class;
            Method testFunc = clientClass.getMethod(funcName, param.getClass());

            // start mock server
            MockMilvusServer server = startServer();
            MilvusServiceClient client = startClient();

            // test return ok with correct input
            R<P> resp = (R<P>) testFunc.invoke(client, param);
            assertEquals(R.Status.Success.getCode(), resp.getStatus());

            // stop mock server
            server.stop();

            // test return error without server
            resp = (R<P>) testFunc.invoke(client, param);
            assertNotEquals(R.Status.Success.getCode(), resp.getStatus());

            // test return error when client channel is shutdown
            client.close();
            resp = (R<P>) testFunc.invoke(client, param);
            assertEquals(R.Status.ClientNotConnected.getCode(), resp.getStatus());
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            System.out.println(e.toString());
        }
    }

    @Test
    void connectParam() {
        System.out.println(System.getProperty("os.name"));
        System.out.println(System.getProperty("os.arch"));

        String host = "dummyHost";
        int port = 100;
        long connectTimeoutMs = 1;
        long keepAliveTimeMs = 2;
        long keepAliveTimeoutMs = 3;
        long idleTimeoutMs = 5;
        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .withConnectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .withKeepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(keepAliveTimeoutMs, TimeUnit.NANOSECONDS)
                .keepAliveWithoutCalls(true)
                .withIdleTimeout(idleTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        assertEquals(host.compareTo(connectParam.getHost()), 0);
        assertEquals(connectParam.getPort(), port);
        assertEquals(connectParam.getConnectTimeoutMs(), connectTimeoutMs);
        assertEquals(connectParam.getKeepAliveTimeMs(), keepAliveTimeMs);
        assertEquals(connectParam.getKeepAliveTimeoutMs(), keepAliveTimeoutMs);
        assertTrue(connectParam.isKeepAliveWithoutCalls());
        assertEquals(connectParam.getIdleTimeoutMs(), idleTimeoutMs);
    }

    @Test
    void createCollectionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
            FieldType.newBuilder()
                    .withName("")
                    .withDataType(DataType.Int64)
                    .build()
        );

        assertThrows(ParamException.class, () ->
            FieldType.newBuilder()
                    .withName("userID")
                    .build()
        );

        assertThrows(ParamException.class, () ->
            FieldType.newBuilder()
                    .withName("userID")
                    .withDataType(DataType.FloatVector)
                    .build()
        );

        assertThrows(ParamException.class, () ->
            CreateCollectionParam
                    .newBuilder()
                    .withCollectionName("collection1")
                    .withShardsNum(2)
                    .build()
        );

        FieldType fieldType1 = FieldType.newBuilder()
                .withName("userID")
                .withDescription("userId")
                .withDataType(DataType.Int64)
                .withAutoID(true)
                .withPrimaryKey(true)
                .build();

        assertThrows(ParamException.class, () ->
            CreateCollectionParam
                    .newBuilder()
                    .withCollectionName("")
                    .withShardsNum(2)
                    .addFieldType(fieldType1)
                    .build()
        );

        assertThrows(ParamException.class, () ->
                CreateCollectionParam
                        .newBuilder()
                        .withCollectionName("collection1")
                        .withShardsNum(0)
                        .addFieldType(fieldType1)
                    .build()
        );

        List<FieldType> fields = Collections.singletonList(null);
        assertThrows(ParamException.class, () ->
                CreateCollectionParam
                        .newBuilder()
                        .withCollectionName("collection1")
                        .withShardsNum(0)
                        .withFieldTypes(fields)
                    .build()
        );
    }


    @Test
    void createCollection() {
        FieldType fieldType1 = FieldType.newBuilder()
                .withName("userID")
                .withDescription("userId")
                .withDataType(DataType.Int64)
                .withAutoID(true)
                .withPrimaryKey(true)
                .build();

        CreateCollectionParam param = CreateCollectionParam
                .newBuilder()
                .withCollectionName("collection1")
                .withShardsNum(2)
                .addFieldType(fieldType1)
                .build();

        testFuncByName("createCollection", param);
    }

    @Test
    void describeCollectionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
                DescribeCollectionParam.newBuilder()
                        .withCollectionName("")
                        .build()
        );
    }

    @Test
    void describeCollection() {
        DescribeCollectionParam param = DescribeCollectionParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("describeCollection", param);
    }

    @Test
    void dropCollectionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
            DropCollectionParam.newBuilder()
                    .withCollectionName("")
                    .build()
        );
    }

    @Test
    void dropCollection() {
        DropCollectionParam param = DropCollectionParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("dropCollection", param);
    }

    @Test
    void getCollectionStatisticsParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
            GetCollectionStatisticsParam.newBuilder()
                    .withCollectionName("")
                    .build()
        );
    }

    @Test
    void getCollectionStatistics() {
        // start mock server
        MockMilvusServer server = startServer();
        MilvusServiceClient client = startClient();

        final String collectionName = "collection1";
        GetCollectionStatisticsParam param = GetCollectionStatisticsParam.newBuilder()
                .withCollectionName(collectionName)
                .withFlush(Boolean.TRUE)
                .build();

        // test return ok with correct input
        final long segmentID = 2021L;
        mockServerImpl.setFlushResponse(FlushResponse.newBuilder()
                .putCollSegIDs(collectionName, LongArray.newBuilder().addData(segmentID).build())
                .build());
        mockServerImpl.setGetPersistentSegmentInfoResponse(GetPersistentSegmentInfoResponse.newBuilder()
                .addInfos(PersistentSegmentInfo.newBuilder()
                        .setSegmentID(segmentID)
                        .setState(SegmentState.Flushing)
                        .build())
                .build());

        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                System.out.println(e.toString());
            }
            mockServerImpl.setGetPersistentSegmentInfoResponse(GetPersistentSegmentInfoResponse.newBuilder()
                    .addInfos(PersistentSegmentInfo.newBuilder()
                            .setSegmentID(segmentID)
                            .setState(SegmentState.Flushed)
                            .build())
                    .build());
        },"RefreshMemState").start();

        R<GetCollectionStatisticsResponse> resp = client.getCollectionStatistics(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // stop mock server
        server.stop();

        // test return error without server
        resp = client.getCollectionStatistics(param);
        assertNotEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return error when client channel is shutdown
        client.close();
        resp = client.getCollectionStatistics(param);
        assertEquals(R.Status.ClientNotConnected.getCode(), resp.getStatus());
    }

    @Test
    void hasCollectionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
            HasCollectionParam.newBuilder()
                    .withCollectionName("")
                    .build()
        );
    }

    @Test
    void hasCollection() {
        HasCollectionParam param = HasCollectionParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("hasCollection", param);
    }

    @Test
    void loadCollectionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
            LoadCollectionParam.newBuilder()
                    .withCollectionName("")
                    .build()
        );

        assertThrows(ParamException.class, () ->
            LoadCollectionParam.newBuilder()
                    .withCollectionName("collection1")
                    .withSyncLoad(Boolean.TRUE)
                    .withSyncLoadWaitingInterval(0L)
                    .build()
        );

        assertThrows(ParamException.class, () ->
            LoadCollectionParam.newBuilder()
                    .withCollectionName("collection1")
                    .withSyncLoad(Boolean.TRUE)
                    .withSyncLoadWaitingInterval(-1L)
                    .build()
        );

        assertThrows(ParamException.class, () ->
            LoadCollectionParam.newBuilder()
                    .withCollectionName("collection1")
                    .withSyncLoad(Boolean.TRUE)
                    .withSyncLoadWaitingInterval(Constant.MAX_WAITING_LOADING_INTERVAL + 1)
                    .build()
        );

        assertThrows(ParamException.class, () ->
                LoadCollectionParam.newBuilder()
                        .withCollectionName("collection1")
                        .withSyncLoad(Boolean.TRUE)
                        .withSyncLoadWaitingTimeout(0L)
                        .build()
        );

        assertThrows(ParamException.class, () ->
                LoadCollectionParam.newBuilder()
                        .withCollectionName("collection1")
                        .withSyncLoad(Boolean.TRUE)
                        .withSyncLoadWaitingTimeout(-1L)
                        .build()
        );

        assertThrows(ParamException.class, () ->
                LoadCollectionParam.newBuilder()
                        .withCollectionName("collection1")
                        .withSyncLoad(Boolean.TRUE)
                        .withSyncLoadWaitingTimeout(Constant.MAX_WAITING_LOADING_TIMEOUT + 1)
                        .build()
        );
    }

    @Test
    void loadCollection() {
        // start mock server
        MockMilvusServer server = startServer();
        MilvusServiceClient client = startClient();

        String collectionName = "collection1";
        LoadCollectionParam param = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSyncLoad(Boolean.FALSE)
                .build();

        // test return ok with correct input
        R<RpcStatus> resp = client.loadCollection(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return ok for sync mode loading
        mockServerImpl.setShowCollectionsResponse(ShowCollectionsResponse.newBuilder()
                .addCollectionNames(collectionName)
                .addInMemoryPercentages(0)
                .build());

        new Thread(() -> {
            try {
                for (int i = 0; i <= 10; ++i) {
                    TimeUnit.MILLISECONDS.sleep(100);
                    mockServerImpl.setShowCollectionsResponse(ShowCollectionsResponse.newBuilder()
                            .addCollectionNames(collectionName)
                            .addInMemoryPercentages(i * 10)
                            .build());
                }
            } catch (InterruptedException e) {
                mockServerImpl.setShowCollectionsResponse(ShowCollectionsResponse.newBuilder()
                        .addCollectionNames(collectionName)
                        .addInMemoryPercentages(100)
                        .build());
            }
        },"RefreshMemState").start();

        param = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingInterval(100L)
                .build();
        resp = client.loadCollection(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // stop mock server
        server.stop();

        // test return error without server
        resp = client.loadCollection(param);
        assertNotEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return error when client channel is shutdown
        client.close();
        resp = client.loadCollection(param);
        assertEquals(R.Status.ClientNotConnected.getCode(), resp.getStatus());
    }

    @Test
    void releaseCollectionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () ->
            ReleaseCollectionParam.newBuilder()
                    .withCollectionName("")
                    .build()
        );
    }

    @Test
    void releaseCollection() {
        ReleaseCollectionParam param = ReleaseCollectionParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("releaseCollection", param);
    }

    @Test
    void showCollectionsParam() {
        // test throw exception with illegal input
        List<String> names = new ArrayList<>();
        names.add(null);
        assertThrows(ParamException.class, () ->
            ShowCollectionsParam.newBuilder()
                    .withCollectionNames(names)
                    .build()
        );

        assertThrows(ParamException.class, () ->
            ShowCollectionsParam.newBuilder()
                    .addCollectionName("")
                    .build()
        );

        // verify internal param
        ShowCollectionsParam param = ShowCollectionsParam.newBuilder()
                .build();
        assertEquals(param.getShowType(), ShowType.All);

        param = ShowCollectionsParam.newBuilder()
                .addCollectionName("collection1")
                .build();
        assertEquals(param.getShowType(), ShowType.InMemory);
    }

    @Test
    void showCollections() {
        ShowCollectionsParam param = ShowCollectionsParam.newBuilder()
                .addCollectionName("collection1")
                .addCollectionName("collection2")
                .build();

        testFuncByName("showCollections", param);
    }

    @Test
    void flushParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("")
                .build()
        );

        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("collection1")
                .withSyncFlush(Boolean.TRUE)
                .withSyncFlushWaitingInterval(0L)
                .build()
        );

        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("collection1")
                .withSyncFlush(Boolean.TRUE)
                .withSyncFlushWaitingInterval(-1L)
                .build()
        );

        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("collection1")
                .withSyncFlush(Boolean.TRUE)
                .withSyncFlushWaitingInterval(Constant.MAX_WAITING_FLUSHING_INTERVAL + 1)
                .build()
        );

        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("collection1")
                .withSyncFlush(Boolean.TRUE)
                .withSyncFlushWaitingTimeout(0L)
                .build()
        );

        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("collection1")
                .withSyncFlush(Boolean.TRUE)
                .withSyncFlushWaitingTimeout(-1L)
                .build()
        );

        assertThrows(ParamException.class, () -> FlushParam.newBuilder()
                .addCollectionName("collection1")
                .withSyncFlush(Boolean.TRUE)
                .withSyncFlushWaitingTimeout(Constant.MAX_WAITING_FLUSHING_TIMEOUT + 1)
                .build()
        );
    }

    @Test
    void createPartitionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> CreatePartitionParam.newBuilder()
                .withCollectionName("")
                .withPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> CreatePartitionParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("")
                .build()
        );
    }

    @Test
    void createPartition() {
        CreatePartitionParam param = CreatePartitionParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("partition1")
                .build();

        testFuncByName("createPartition", param);
    }

    @Test
    void dropPartitionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> DropPartitionParam.newBuilder()
                .withCollectionName("")
                .withPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> DropPartitionParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("")
                .build()
        );
    }

    @Test
    void dropPartition() {
        DropPartitionParam param = DropPartitionParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("partition1")
                .build();

        testFuncByName("dropPartition", param);
    }

    @Test
    void hasPartitionParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> HasPartitionParam.newBuilder()
                .withCollectionName("")
                .withPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> HasPartitionParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("")
                .build()
        );
    }

    @Test
    void hasPartition() {
        HasPartitionParam param = HasPartitionParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("partition1")
                .build();

        testFuncByName("hasPartition", param);
    }

    @Test
    void loadPartitionsParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("")
                .addPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("")
                .build()
        );

        List<String> names = new ArrayList<>();
        names.add(null);
        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(names)
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingInterval(0L)
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingInterval(-1L)
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingInterval(Constant.MAX_WAITING_LOADING_INTERVAL + 1)
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingTimeout(0L)
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingTimeout(-1L)
                .build()
        );

        assertThrows(ParamException.class, () -> LoadPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingTimeout(Constant.MAX_WAITING_LOADING_TIMEOUT + 1)
                .build()
        );
    }

    @Test
    void loadPartitions() {
        // start mock server
        MockMilvusServer server = startServer();
        MilvusServiceClient client = startClient();

        String collectionName = "collection1";
        String partitionName = "partition1";
        LoadPartitionsParam param = LoadPartitionsParam.newBuilder()
                .withCollectionName(collectionName)
                .addPartitionName(partitionName)
                .withSyncLoad(Boolean.FALSE)
                .build();

        // test return ok with correct input
        R<RpcStatus> resp = client.loadPartitions(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return ok for sync mode loading
        mockServerImpl.setShowPartitionsResponse(ShowPartitionsResponse.newBuilder()
                .addPartitionNames(partitionName)
                .addInMemoryPercentages(0)
                .build());

        new Thread(() -> {
            try {
                for (int i = 0; i <= 10; ++i) {
                    TimeUnit.MILLISECONDS.sleep(100);
                    mockServerImpl.setShowPartitionsResponse(ShowPartitionsResponse.newBuilder()
                            .addPartitionNames(partitionName)
                            .addInMemoryPercentages(i*10)
                            .build());
                }
            } catch (InterruptedException e) {
                mockServerImpl.setShowPartitionsResponse(ShowPartitionsResponse.newBuilder()
                        .addPartitionNames(partitionName)
                        .addInMemoryPercentages(100)
                        .build());
            }
        },"RefreshMemState").start();

        param = LoadPartitionsParam.newBuilder()
                .withCollectionName(collectionName)
                .addPartitionName(partitionName)
                .withSyncLoad(Boolean.TRUE)
                .withSyncLoadWaitingInterval(100L)
                .build();
        resp = client.loadPartitions(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // stop mock server
        server.stop();

        // test return error without server
        resp = client.loadPartitions(param);
        assertNotEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return error when client channel is shutdown
        client.close();
        resp = client.loadPartitions(param);
        assertEquals(R.Status.ClientNotConnected.getCode(), resp.getStatus());
    }

    @Test
    void releasePartitionsParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> ReleasePartitionsParam.newBuilder()
                .withCollectionName("")
                .addPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> ReleasePartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("")
                .build()
        );

        List<String> names = new ArrayList<>();
        names.add(null);
        assertThrows(ParamException.class, () -> ReleasePartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(names)
                .build()
        );
    }

    @Test
    void releasePartitions() {
        ReleasePartitionsParam param = ReleasePartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .addPartitionName("partition1")
                .build();

        testFuncByName("releasePartitions", param);
    }

    @Test
    void getPartitionStatisticsParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> GetPartitionStatisticsParam.newBuilder()
                .withCollectionName("")
                .withPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> GetPartitionStatisticsParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("")
                .build()
        );
    }

    @Test
    void getPartitionStatistics() {
        GetPartitionStatisticsParam param = GetPartitionStatisticsParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("partition1")
                .build();

        testFuncByName("getPartitionStatistics", param);
    }

    @Test
    void showPartitionsParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> ShowPartitionsParam.newBuilder()
                .withCollectionName("")
                .addPartitionName("partition1")
                .build()
        );

        assertThrows(ParamException.class, () -> ShowPartitionsParam.newBuilder()
                .withCollectionName("collection1`")
                .addPartitionName("")
                .build()
        );

        List<String> names = new ArrayList<>();
        names.add(null);
        assertThrows(ParamException.class, () -> ShowPartitionsParam.newBuilder()
                .withCollectionName("collection1`")
                .withPartitionNames(names)
                .build()
        );

        // verify internal param
        ShowPartitionsParam param = ShowPartitionsParam.newBuilder()
                .withCollectionName("collection1`")
                .build();
        assertEquals(param.getShowType(), ShowType.All);

        param = ShowPartitionsParam.newBuilder()
                .withCollectionName("collection1`")
                .addPartitionName("partition1")
                .build();
        assertEquals(param.getShowType(), ShowType.InMemory);
    }

    @Test
    void showPartitions() {
        ShowPartitionsParam param = ShowPartitionsParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("showPartitions", param);
    }

    @Test
    void createAliasParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> CreateAliasParam.newBuilder()
                .withCollectionName("")
                .withAlias("alias1")
                .build()
        );

        assertThrows(ParamException.class, () -> CreateAliasParam.newBuilder()
                .withCollectionName("collection1")
                .withAlias("")
                .build()
        );
    }

    @Test
    void createAlias() {
        CreateAliasParam param = CreateAliasParam.newBuilder()
                .withCollectionName("collection1")
                .withAlias("alias1")
                .build();

        testFuncByName("createAlias", param);
    }

    @Test
    void dropAliasParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> DropAliasParam.newBuilder()
                .withAlias("")
                .build()
        );
    }

    @Test
    void dropAlias() {
        DropAliasParam param = DropAliasParam.newBuilder()
                .withAlias("alias1")
                .build();

        testFuncByName("dropAlias", param);
    }

    @Test
    void alterAliasParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> CreateAliasParam.newBuilder()
                .withCollectionName("")
                .withAlias("alias1")
                .build()
        );

        assertThrows(ParamException.class, () -> CreateAliasParam.newBuilder()
                .withCollectionName("collection1")
                .withAlias("")
                .build()
        );
    }

    @Test
    void alterAlias() {
        AlterAliasParam param = AlterAliasParam.newBuilder()
                .withCollectionName("collection1")
                .withAlias("alias1")
                .build();

        testFuncByName("alterAlias", param);
    }

    @Test
    void createIndexParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> CreateIndexParam.newBuilder()
                .withCollectionName("")
                .withFieldName("field1")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> CreateIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> CreateIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .withIndexType(IndexType.INVALID)
                .withMetricType(MetricType.L2)
                .withExtraParam("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> CreateIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.INVALID)
                .withExtraParam("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> CreateIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("")
                .build()
        );
    }

    @Test
    void createIndex() {
        // start mock server
        MockMilvusServer server = startServer();
        MilvusServiceClient client = startClient();

        // test return ok for sync mode loading
        mockServerImpl.setGetIndexStateResponse(GetIndexStateResponse.newBuilder()
                .setState(IndexState.InProgress)
                .build());

        new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(1);
                mockServerImpl.setGetIndexStateResponse(GetIndexStateResponse.newBuilder()
                        .setState(IndexState.Finished)
                        .build());
            } catch (InterruptedException e) {
                mockServerImpl.setGetIndexStateResponse(GetIndexStateResponse.newBuilder()
                        .setState(IndexState.Finished)
                        .build());
            }
        }, "RefreshIndexState").start();

        CreateIndexParam param = CreateIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("dummy")
                .withSyncMode(Boolean.TRUE)
                .withSyncWaitingInterval(500L)
                .withSyncWaitingTimeout(2L)
                .build();

        // test return ok with correct input
        R<RpcStatus> resp = client.createIndex(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // stop mock server
        server.stop();

        // test return error without server
        resp = client.createIndex(param);
        assertNotEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return error when client channel is shutdown
        client.close();
        resp = client.createIndex(param);
        assertEquals(R.Status.ClientNotConnected.getCode(), resp.getStatus());
    }

    @Test
    void describeIndexParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> DescribeIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("")
                .build()
        );

        assertThrows(ParamException.class, () -> DescribeIndexParam.newBuilder()
                .withCollectionName("")
                .withFieldName("field1")
                .build()
        );
    }

    @Test
    void describeIndex() {
        DescribeIndexParam param = DescribeIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .build();

        testFuncByName("describeIndex", param);
    }

    @Test
    void getIndexStateParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> GetIndexStateParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("")
                .build()
        );

        assertThrows(ParamException.class, () -> GetIndexStateParam.newBuilder()
                .withCollectionName("")
                .withFieldName("field1")
                .build()
        );
    }

    @Test
    void getIndexState() {
        GetIndexStateParam param = GetIndexStateParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .build();

        testFuncByName("getIndexState", param);
    }

    @Test
    void getIndexBuildProgressParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> GetIndexBuildProgressParam.newBuilder()
                .withCollectionName("")
                .build()
        );
    }

    @Test
    void getIndexBuildProgress() {
        GetIndexBuildProgressParam param = GetIndexBuildProgressParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("getIndexBuildProgress", param);
    }

    @Test
    void dropIndexParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> DropIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("")
                .build()
        );

        assertThrows(ParamException.class, () -> DropIndexParam.newBuilder()
                .withCollectionName("")
                .withFieldName("field1")
                .build()
        );
    }

    @Test
    void dropIndex() {
        DropIndexParam param = DropIndexParam.newBuilder()
                .withCollectionName("collection1")
                .withFieldName("field1")
                .build();

        testFuncByName("dropIndex", param);
    }

    @Test
    void insertParam() {
        // test throw exception with illegal input
        List<InsertParam.Field> fields = new ArrayList<>();

        // collection is empty
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("")
                .withFields(fields)
                .build()
        );

        // fields is empty
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        // field is null
        fields.add(null);
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        // field name is empty
        fields.clear();
        List<Long> ids = new ArrayList<>();
        fields.add(new InsertParam.Field("", DataType.Int64, ids));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        // field row count is 0
        fields.clear();
        fields.add(new InsertParam.Field("field1", DataType.Int64, ids));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        // field row count not equal
        fields.clear();
        ids.add(1L);
        fields.add(new InsertParam.Field("field1", DataType.Int64, ids));
        List<List<Float>> vectors = new ArrayList<>();
        fields.add(new InsertParam.Field("field2", DataType.FloatVector, vectors));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        // wrong type
        fields.clear();
        List<String> fakeVectors1 = new ArrayList<>();
        fields.add(new InsertParam.Field("field2", DataType.FloatVector, fakeVectors1));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        fields.clear();
        List<List<String>> fakeVectors2 = new ArrayList<>();
        fields.add(new InsertParam.Field("field2", DataType.FloatVector, fakeVectors2));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        fields.clear();
        fields.add(new InsertParam.Field("field2", DataType.BinaryVector, fakeVectors1));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        // vector dimension not equal
        fields.clear();
        List<Float> vector1 = Arrays.asList(0.1F, 0.2F, 0.3F);
        List<Float> vector2 = Arrays.asList(0.1F, 0.2F);
        vectors.add(vector1);
        vectors.add(vector2);
        fields.add(new InsertParam.Field("field1", DataType.FloatVector, vectors));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );

        fields.clear();
        ByteBuffer buf1 = ByteBuffer.allocate(1);
        buf1.put((byte) 1);
        ByteBuffer buf2 = ByteBuffer.allocate(2);
        buf2.put((byte) 1);
        buf2.put((byte) 2);
        List<ByteBuffer> binVectors = Arrays.asList(buf1, buf2);
        fields.add(new InsertParam.Field("field2", DataType.BinaryVector, binVectors));
        assertThrows(ParamException.class, () -> InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withFields(fields)
                .build()
        );
    }

    @Test
    void insert() {
        List<InsertParam.Field> fields = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        List<Integer> nVal = new ArrayList<>();
        List<Boolean> bVal = new ArrayList<>();
        List<Float> fVal = new ArrayList<>();
        List<Double> dVal = new ArrayList<>();
        List<String> sVal = new ArrayList<>();
        List<ByteBuffer> bVectors = new ArrayList<>();
        List<List<Float>> fVectors = new ArrayList<>();
        for (int i = 0; i < 3; ++i) {
            ids.add((long)i);
            nVal.add(i);
            bVal.add(Boolean.TRUE);
            fVal.add(0.5f);
            dVal.add(1.0);
            sVal.add(String.valueOf(i));
            ByteBuffer buf = ByteBuffer.allocate(2);
            buf.put((byte)1);
            buf.put((byte)2);
            bVectors.add(buf);
            List<Float> vec = Arrays.asList(0.1f, 0.2f);
            fVectors.add(vec);
        }
        fields.add(new InsertParam.Field("field1", DataType.Int64, ids));
        fields.add(new InsertParam.Field("field2", DataType.Int8, nVal));
        fields.add(new InsertParam.Field("field3", DataType.Bool, bVal));
        fields.add(new InsertParam.Field("field4", DataType.Float, fVal));
        fields.add(new InsertParam.Field("field5", DataType.Double, dVal));
        fields.add(new InsertParam.Field("field6", DataType.String, sVal));
        fields.add(new InsertParam.Field("field7", DataType.FloatVector, fVectors));
        fields.add(new InsertParam.Field("field8", DataType.BinaryVector, bVectors));
        InsertParam param = InsertParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("partition1")
                .withFields(fields)
                .build();

        testFuncByName("insert", param);
    }

    @Test
    void deleteParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> DeleteParam.newBuilder()
                .withCollectionName("")
                .withExpr("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> DeleteParam.newBuilder()
                .withCollectionName("collection1")
                .withExpr("")
                .build()
        );
    }

    @Test
    void delete() {
        DeleteParam param = DeleteParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionName("partition1")
                .withExpr("dummy")
                .build();

        testFuncByName("delete", param);
    }

    @Test
    void searchParam() {
        // test throw exception with illegal input
        List<String> partitions = Collections.singletonList("partition1");
        List<String> outputFields = Collections.singletonList("field2");
        List<List<Float>> vectors = new ArrayList<>();
        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("field1")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(vectors)
                .withExpr("dummy")
                .build()
        );

        List<Float> vector1 = Collections.singletonList(0.1F);
        vectors.add(vector1);
        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("field1")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(vectors)
                .withExpr("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(vectors)
                .withExpr("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.INVALID)
                .withTopK(5)
                .withVectors(vectors)
                .withExpr("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.IP)
                .withTopK(0)
                .withVectors(vectors)
                .withExpr("dummy")
                .build()
        );

        // vector type illegal
        List<String> fakeVectors1 = Collections.singletonList("fake");
        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(fakeVectors1)
                .withExpr("dummy")
                .build()
        );

        List<List<String>> fakeVectors2 = Collections.singletonList(fakeVectors1);
        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(fakeVectors2)
                .withExpr("dummy")
                .build()
        );

        // vector dimension not equal
        List<Float> vector2 = Arrays.asList(0.1F, 0.2F);
        vectors.add(vector2);
        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(vectors)
                .withExpr("dummy")
                .build()
        );

        ByteBuffer buf1 = ByteBuffer.allocate(1);
        buf1.put((byte) 1);
        ByteBuffer buf2 = ByteBuffer.allocate(2);
        buf2.put((byte) 1);
        buf2.put((byte) 2);
        List<ByteBuffer> binVectors = Arrays.asList(buf1, buf2);
        assertThrows(ParamException.class, () -> SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(binVectors)
                .withExpr("dummy")
                .build()
        );
    }

    @Test
    void search() {
        // start mock server
        MockMilvusServer server = startServer();
        MilvusServiceClient client = startClient();

        List<String> partitions = Collections.singletonList("partition1");
        List<String> outputFields = Collections.singletonList("field2");

        // test return ok with correct input
        List<List<Float>> vectors = new ArrayList<>();
        List<Float> vector1 = Arrays.asList(0.1f, 0.2f);
        vectors.add(vector1);
        SearchParam param = SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("field1")
                .withMetricType(MetricType.IP)
                .withTopK(5)
                .withVectors(vectors)
                .withExpr("dummy")
                .build();
        R<SearchResults> resp = client.search(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        List<ByteBuffer> bVectors = new ArrayList<>();
        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.put((byte)1);
        buf.put((byte)2);
        bVectors.add(buf);
        param = SearchParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withParams("dummy")
                .withOutFields(outputFields)
                .withVectorFieldName("field1")
                .withMetricType(MetricType.HAMMING)
                .withTopK(5)
                .withVectors(bVectors)
                .withExpr("dummy")
                .build();

        resp = client.search(param);
        assertEquals(R.Status.Success.getCode(), resp.getStatus());

        // stop mock server
        server.stop();

        // test return error without server
        resp = client.search(param);
        assertNotEquals(R.Status.Success.getCode(), resp.getStatus());

        // test return error when client channel is shutdown
        client.close();
        resp = client.search(param);
        assertEquals(R.Status.ClientNotConnected.getCode(), resp.getStatus());
    }

    @Test
    void queryParam() {
        // test throw exception with illegal input
        List<String> partitions = Collections.singletonList("partition1");
        List<String> outputFields = Collections.singletonList("field2");
        assertThrows(ParamException.class, () -> QueryParam.newBuilder()
                .withCollectionName("")
                .withPartitionNames(partitions)
                .withOutFields(outputFields)
                .withExpr("dummy")
                .build()
        );

        assertThrows(ParamException.class, () -> QueryParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withOutFields(outputFields)
                .withExpr("")
                .build()
        );
    }

    @Test
    void query() {
        List<String> partitions = Collections.singletonList("partition1");
        List<String> outputFields = Collections.singletonList("field2");
        QueryParam param = QueryParam.newBuilder()
                .withCollectionName("collection1")
                .withPartitionNames(partitions)
                .withOutFields(outputFields)
                .withExpr("dummy")
                .build();

        testFuncByName("query", param);
    }

    @Test
    void calcDistanceParam() {
        // test throw exception with illegal input
        List<List<Float>> vectorsLeft = new ArrayList<>();
        List<List<Float>> vectorsRight = new ArrayList<>();
        List<Float> vector1 = Collections.singletonList(0.1F);
        List<Float> vector2 = Collections.singletonList(0.1F);
        vectorsLeft.add(vector1);
        vectorsRight.add(vector2);

        assertThrows(ParamException.class, () -> CalcDistanceParam.newBuilder()
                .withVectorsLeft(vectorsLeft)
                .withVectorsRight(vectorsRight)
                .withMetricType(MetricType.INVALID)
                .build()
        );

        vectorsLeft.clear();
        assertThrows(ParamException.class, () -> CalcDistanceParam.newBuilder()
                .withVectorsLeft(vectorsLeft)
                .withVectorsRight(vectorsRight)
                .withMetricType(MetricType.IP)
                .build()
        );

        vectorsLeft.add(vector1);
        vectorsRight.clear();
        assertThrows(ParamException.class, () -> CalcDistanceParam.newBuilder()
                .withVectorsLeft(vectorsLeft)
                .withVectorsRight(vectorsRight)
                .withMetricType(MetricType.IP)
                .build()
        );

        // vector dimension not equal
        vectorsRight.add(vector2);
        List<Float> vector3 = Arrays.asList(0.1F, 0.2F);
        vectorsLeft.add(vector3);
        assertThrows(ParamException.class, () -> CalcDistanceParam.newBuilder()
                .withVectorsLeft(vectorsLeft)
                .withVectorsRight(vectorsRight)
                .withMetricType(MetricType.IP)
                .build()
        );

        vectorsLeft.clear();
        vectorsLeft.add(vector1);
        List<Float> vector4 = Arrays.asList(0.1F, 0.2F);
        vectorsRight.add(vector4);
        assertThrows(ParamException.class, () -> CalcDistanceParam.newBuilder()
                .withVectorsLeft(vectorsLeft)
                .withVectorsRight(vectorsRight)
                .withMetricType(MetricType.IP)
                .build()
        );
    }

    @Test
    void calcDistance() {
        List<List<Float>> vectorsLeft = new ArrayList<>();
        List<List<Float>> vectorsRight = new ArrayList<>();
        List<Float> vector1 = Collections.singletonList(0.1F);
        List<Float> vector2 = Collections.singletonList(0.1F);
        vectorsLeft.add(vector1);
        vectorsRight.add(vector2);
        CalcDistanceParam param = CalcDistanceParam.newBuilder()
                .withVectorsLeft(vectorsLeft)
                .withVectorsRight(vectorsRight)
                .withMetricType(MetricType.L2)
                .build();

        testFuncByName("calcDistance", param);
    }

    @Test
    void getMetricsParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> GetMetricsParam.newBuilder()
                .withRequest("")
                .build()
        );
    }

    @Test
    void getMetrics() {
        GetMetricsParam param = GetMetricsParam.newBuilder()
                .withRequest("{}")
                .build();

        testFuncByName("getMetrics", param);
    }

    @Test
    void getPersistentSegmentInfoParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> GetPersistentSegmentInfoParam.newBuilder()
                .withCollectionName("")
                .build()
        );
    }

    @Test
    void getPersistentSegmentInfo() {
        GetPersistentSegmentInfoParam param = GetPersistentSegmentInfoParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("getPersistentSegmentInfo", param);
    }

    @Test
    void getQuerySegmentInfoParam() {
        // test throw exception with illegal input
        assertThrows(ParamException.class, () -> GetQuerySegmentInfoParam
                .newBuilder()
                .withCollectionName("")
                .build()
        );
    }

    @Test
    void getQuerySegmentInfo() {
        GetQuerySegmentInfoParam param = GetQuerySegmentInfoParam.newBuilder()
                .withCollectionName("collection1")
                .build();

        testFuncByName("getQuerySegmentInfo", param);
    }
}