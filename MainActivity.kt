package com.example.ecomerapp


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EcomerApp()
                }
            }
        }
    }
}

// Màn hình đăng nhập
@Composable
fun EcomerApp() {
    val auth = FirebaseAuth.getInstance()
    var currentUserEmail by remember { mutableStateOf(auth.currentUser?.email) }

    if (currentUserEmail == null) {
        LoginScreen(
            onLoginSuccess = { email ->
                currentUserEmail = email
            }
        )
    } else {
        // PHÂN QUYỀN cho admin
        val isAdmin = currentUserEmail!!.contains("admin", ignoreCase = true)
        ProductAdminScreen(
            userEmail = currentUserEmail!!,
            isAdmin = isAdmin,
            onLogout = {
                auth.signOut()
                currentUserEmail = null
            }
        )
    }
}

// Màn hình đăng nhập
@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("ECOMER LOGIN", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mật khẩu (tối thiểu 6 ký tự)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        if (email.isBlank() || password.length < 6) {
                            Toast.makeText(context, "Nhập email và mật khẩu >= 6 ký tự", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        auth.signInWithEmailAndPassword(email.trim(), password.trim())
                            .addOnSuccessListener {
                                isLoading = false
                                onLoginSuccess(email.trim())
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Lỗi đăng nhập: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Đăng nhập")
                }

                OutlinedButton(
                    onClick = {
                        if (email.isBlank() || password.length < 6) {
                            Toast.makeText(context, "Nhập email và mật khẩu >= 6 ký tự", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        isLoading = true
                        auth.createUserWithEmailAndPassword(email.trim(), password.trim())
                            .addOnSuccessListener {
                                isLoading = false
                                Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess(email.trim())
                            }
                            .addOnFailureListener {
                                isLoading = false
                                Toast.makeText(context, "Lỗi đăng ký: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Đăng ký")
                }
            }
        }
    }
}

//Màn hình quản lý sản phẩm
@Composable
fun ProductAdminScreen(
    userEmail: String,
    isAdmin: Boolean,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val collectionRef = db.collection("products")

    var tenSanPham by remember { mutableStateOf("") }
    var loaiSanPham by remember { mutableStateOf("") }
    var gia by remember { mutableStateOf("") }
    var imageString by remember { mutableStateOf("") }

    var editingDocId by remember { mutableStateOf<String?>(null) }

    var productList by remember { mutableStateOf(listOf<Product>()) }

    LaunchedEffect(Unit) {
        collectionRef.addSnapshotListener { snapshot, error ->
            if (error == null && snapshot != null) {
                val items = snapshot.documents.map { doc ->
                    Product(
                        docId = doc.id,
                        tenSanPham = doc.getString("tenSanPham") ?: "",
                        loaiSanPham = doc.getString("loaiSanPham") ?: "",
                        gia = doc.getString("gia") ?: "",
                        image = doc.getString("image") ?: ""
                    )
                }
                productList = items
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val base64 = uriToBase64(context, it)
            if (base64 != null) {
                imageString = base64
                Toast.makeText(context, "Đã chọn ảnh thành công!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Thanh tiêu đề & Nút Đăng xuất
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isAdmin) "PHÂN HỆ ADMIN - ECOMER" else "KHÁCH HÀNG - ECOMER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1976D2)
                )
                Text(text = "Tài khoản: $userEmail", fontSize = 12.sp, color = Color.Gray)
            }
            TextButton(onClick = onLogout) {
                Text("Đăng xuất", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (isAdmin) {
            Text(
                text = "Dữ liệu sản phẩm",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = tenSanPham,
                onValueChange = { tenSanPham = it },
                label = { Text("Tên sản phẩm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = loaiSanPham,
                onValueChange = { loaiSanPham = it },
                label = { Text("Loại sản phẩm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = gia,
                onValueChange = { gia = it },
                label = { Text("Giá sản phẩm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (imageString.startsWith("data:image") || imageString.length > 200) "[Đã chọn ảnh từ máy]" else imageString,
                    onValueChange = { imageString = it },
                    label = { Text("Image (Link ảnh hoặc chọn từ máy)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") }
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Chọn ảnh")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chọn ảnh")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (tenSanPham.isBlank() || loaiSanPham.isBlank() || gia.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập đủ Tên, Loại và Giá!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val productData = hashMapOf(
                            "tenSanPham" to tenSanPham.trim(),
                            "loaiSanPham" to loaiSanPham.trim(),
                            "gia" to gia.trim(),
                            "image" to imageString.trim()
                        )

                        if (editingDocId == null) {
                            collectionRef.add(productData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Thêm sản phẩm thành công!", Toast.LENGTH_SHORT).show()
                                    tenSanPham = ""
                                    loaiSanPham = ""
                                    gia = ""
                                    imageString = ""
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Lỗi khi thêm: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            collectionRef.document(editingDocId!!).set(productData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Cập nhật sản phẩm thành công!", Toast.LENGTH_SHORT).show()
                                    editingDocId = null
                                    tenSanPham = ""
                                    loaiSanPham = ""
                                    gia = ""
                                    imageString = ""
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Lỗi cập nhật: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text(if (editingDocId == null) "THÊM SẢN PHẨM" else "LƯU CẬP NHẬT")
                }

                if (editingDocId != null) {
                    OutlinedButton(
                        onClick = {
                            editingDocId = null
                            tenSanPham = ""
                            loaiSanPham = ""
                            gia = ""
                            imageString = ""
                        }
                    ) {
                        Text("Hủy")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Danh Sách sản phẩm
        Text(
            text = "Danh sách sản phẩm (${productList.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(productList) { product ->
                ProductItemCard(
                    product = product,
                    isAdmin = isAdmin,
                    onEditClick = {
                        // Đổ dữ liệu lên trường nhập
                        editingDocId = product.docId
                        tenSanPham = product.tenSanPham
                        loaiSanPham = product.loaiSanPham
                        gia = product.gia
                        imageString = product.image
                    },
                    onDeleteClick = {
                        // Chức năng xóa
                        collectionRef.document(product.docId).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Đã xóa: ${product.tenSanPham}", Toast.LENGTH_SHORT).show()
                            }
                    }
                )
            }
        }
    }
}

//  GIAO DIỆN TỪNG DÒNG SẢN PHẨM
@Composable
fun ProductItemCard(
    product: Product,
    isAdmin: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            ProductImageView(imageStr = product.image)

            Spacer(modifier = Modifier.width(12.dp))


            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Tên sp: ${product.tenSanPham}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = "Giá sp: ${product.gia}", color = Color(0xFFD32F2F), fontSize = 14.sp)
                Text(text = "Loại sp: ${product.loaiSanPham}", color = Color.DarkGray, fontSize = 14.sp)
            }

            // Nút Sửa và Xóa
            if (isAdmin) {
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Sửa",
                            tint = Color(0xFFFBC02D)
                        )
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa",
                            tint = Color(0xFFD32F2F)
                        )
                    }
                }
            }
        }
    }
}

// Image
@Composable
fun ProductImageView(imageStr: String) {
    val modifier = Modifier
        .size(68.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(Color.LightGray)
        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))

    if (imageStr.isBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, contentDescription = null, tint = Color.DarkGray)
        }
    } else if (imageStr.startsWith("http")) {
        AsyncImage(
            model = imageStr,
            contentDescription = "Product Image",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        val bitmap = remember(imageStr) { base64ToBitmap(imageStr) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Product Image",
                modifier = modifier,
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.DarkGray)
            }
        }
    }
}

//Nén ảnh sang dữ liệu base64 nhằm lưu FireStore
fun uriToBase64(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()


        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 300, 300, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val byteArray = outputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.DEFAULT)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun base64ToBitmap(base64Str: String): Bitmap? {
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}