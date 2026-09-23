package com.example.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Clean architectural interfaces reserved for future phases.
 * DO NOT implement fake or mock networking/AI in Phase 1 or 2.
 */

/**
 * Future P2P transport abstraction for nearby direct device communication (Phase 04/05).
 */
interface NearbyTransport {
    fun observeAvailableEndpoints(): Flow<List<String>>
    suspend fun sendPayload(endpointId: String, payload: ByteArray): Result<Unit>
}

/**
 * Future cloud synchronization abstraction for optional cloud sync (Phase 12).
 */
interface SyncTransport {
    fun observeSyncProgress(): Flow<Float>
    suspend fun syncLocalChanges(): Result<Unit>
}

/**
 * Future local/cloud AI routing abstraction for edge intelligence (Phase 08).
 */
interface AiProvider {
    fun isLocalInferenceSupported(): Boolean
    suspend fun generateOfflineResponse(prompt: String): Result<String>
}

/**
 * Future local knowledge and content provider (Phase 09).
 */
interface LocalKnowledgeProvider {
    suspend fun queryKnowledge(query: String): List<String>
}

/**
 * Future store-and-forward routing abstraction for offline mesh networks (Phase 07).
 */
interface MeshRouter {
    fun observeMeshRouteCount(): Flow<Int>
    suspend fun routePacket(destinationNodeId: String, packet: ByteArray): Result<Unit>
}

interface MessageRepository {
    // Phase 5: Offline P2P Messaging
    fun observeMessages(conversationId: String): Flow<List<Any>>
}

interface FileTransferRepository {
    // Phase 6: P2P File Transfer + Resume
    fun observeTransfers(): Flow<List<Any>>
}

interface PeerDiscoveryRepository {
    // Phase 4: Nearby Device Discovery
    fun observeNearbyPeers(): Flow<List<Any>>
}

interface MeshRoutingRepository {
    // Phase 7: Store-and-Forward Mesh
    fun getRoutingTopology(): Flow<Any?>
}

interface OfflineAIRepository {
    // Phase 8: On-Device AI
    fun isModelReady(): Boolean
}

interface RAGRepository {
    // Phase 9: Local RAG Knowledge Engine
    suspend fun queryLocalKnowledge(query: String): List<String>
}

interface CloudSyncRepository {
    // Phase 12: Cloud Synchronization
    fun observeSyncStatus(): Flow<String>
}

interface EdgeNodeRepository {
    // Phase 11: Edge Node / Community Network
    fun observeLocalNodes(): Flow<List<Any>>
}

