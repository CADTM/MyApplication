package com.example.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object FirestoreSync {
    private const val TAG = "FirestoreSync"
    
    private fun getDb(): FirebaseFirestore? {
        return try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Firestore is not initialized or missing configuration", e)
            null
        }
    }
    
    fun syncContainer(item: ContainerItem) {
        val db = getDb() ?: return
        try {
            val data = hashMapOf(
                "id" to item.id,
                "reportId" to item.reportId,
                "type" to item.type,
                "locationType" to item.locationType,
                "dockNumber" to item.dockNumber,
                "dockCategory" to item.dockCategory,
                "economicNumber" to item.economicNumber,
                "plates" to item.plates,
                "platesLocation" to item.platesLocation,
                "status" to item.status,
                "loadStatus" to item.loadStatus,
                "sealCode" to item.sealCode,
                "company" to item.company,
                "containerSize" to item.containerSize,
                "destination" to item.destination,
                "deliveryDocument" to item.deliveryDocument,
                "processedDate" to item.processedDate,
                "timestamp" to item.timestamp,
                "isStorageOnly" to item.isStorageOnly,
                "mexCalType" to item.mexCalType,
                "receptionDate" to item.receptionDate,
                "materialType" to item.materialType
            )
            
            db.collection("containers")
                .document(item.id.toString())
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Container ${item.id} synced successfully to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to sync Container ${item.id} to Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing container ${item.id} to Firestore", e)
        }
    }
    
    fun deleteContainer(id: Long) {
        val db = getDb() ?: return
        try {
            db.collection("containers")
                .document(id.toString())
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Container $id deleted successfully from Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete Container $id from Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting container $id from Firestore", e)
        }
    }

    fun syncDamagedBox(box: DamagedBox) {
        val db = getDb() ?: return
        try {
            val data = hashMapOf(
                "id" to box.id,
                "reportId" to box.reportId,
                "economicNumber" to box.economicNumber,
                "type" to box.type,
                "damageDescription" to box.damageDescription,
                "timestamp" to box.timestamp
            )
            
            db.collection("damaged_boxes")
                .document(box.id.toString())
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Damaged box ${box.id} synced successfully to Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to sync Damaged box ${box.id} to Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing damaged box ${box.id} to Firestore", e)
        }
    }

    fun deleteDamagedBox(id: Long) {
        val db = getDb() ?: return
        try {
            db.collection("damaged_boxes")
                .document(id.toString())
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Damaged box $id deleted successfully from Firestore")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete Damaged box $id from Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting damaged box $id from Firestore", e)
        }
    }
}
