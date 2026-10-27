package com.example.ui.garage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppSettingsEntity
import com.example.data.model.FuelType
import com.example.data.model.VehicleEntity
import com.example.data.model.VehicleType
import com.example.ui.theme.AmberPersonal
import com.example.ui.theme.BlueCobalt
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NavyDark
import com.example.ui.theme.RedExpense
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateDark
import com.example.ui.theme.SlateLight
import com.example.ui.theme.SlateMedium
import java.util.Locale

@Composable
fun GarageScreen(
    vehicles: List<VehicleEntity>,
    settings: AppSettingsEntity,
    onAddVehicle: (name: String, type: VehicleType, fuel: FuelType, efficiency: Double, wear: Double, isDefault: Boolean) -> Unit,
    onUpdateVehicle: (VehicleEntity) -> Unit,
    onSetDefault: (String) -> Unit,
    onDeleteVehicle: (VehicleEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleEntity?>(null) }

    Box(modifier = modifier.fillMaxSize().background(SlateBackground)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Vehicle Garage",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark
                    )
                    Text(
                        text = "Manage your vehicles, fuel economy & wear costs",
                        fontSize = 12.sp,
                        color = SlateLight
                    )
                }
            }

            if (vehicles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(BlueCobalt.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = BlueCobalt,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Your Garage is Empty",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add your motorcycle, scooter, car, or SUV to auto-detect tailored fuel economy and wear costs.",
                                fontSize = 12.sp,
                                color = SlateLight,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Your First Vehicle")
                            }
                        }
                    }
                }
            } else {
                items(vehicles, key = { it.id }) { vehicle ->
                    VehicleCardItem(
                        vehicle = vehicle,
                        settings = settings,
                        onSetDefault = { onSetDefault(vehicle.id) },
                        onEdit = { editingVehicle = vehicle },
                        onDelete = { onDeleteVehicle(vehicle) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_vehicle_fab"),
            containerColor = BlueCobalt,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Vehicle")
        }

        if (showAddDialog) {
            AddVehicleDialog(
                settings = settings,
                onDismiss = { showAddDialog = false },
                onConfirm = { name, type, fuel, efficiency, wear, isDef ->
                    onAddVehicle(name, type, fuel, efficiency, wear, isDef)
                    showAddDialog = false
                }
            )
        }

        editingVehicle?.let { vehicleToEdit ->
            EditVehicleDialog(
                vehicle = vehicleToEdit,
                settings = settings,
                onDismiss = { editingVehicle = null },
                onConfirm = { updated ->
                    onUpdateVehicle(updated)
                    editingVehicle = null
                }
            )
        }
    }
}

@Composable
fun VehicleCardItem(
    vehicle: VehicleEntity,
    settings: AppSettingsEntity,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vehicleType = vehicle.getParsedVehicleType()
    val fuelType = vehicle.getParsedFuelType()

    val icon = when (vehicleType) {
        VehicleType.MOTORCYCLE, VehicleType.SCOOTER -> Icons.Default.TwoWheeler
        VehicleType.CAR, VehicleType.SUV, VehicleType.VAN, VehicleType.TRUCK -> {
            if (fuelType == FuelType.EV) Icons.Default.ElectricCar else Icons.Default.DirectionsCar
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (vehicle.isDefault) EmeraldGreen.copy(alpha = 0.12f) else BlueCobalt.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = vehicleType.displayName,
                            tint = if (vehicle.isDefault) EmeraldGreen else BlueCobalt,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = vehicle.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark
                            )
                            if (vehicle.isDefault) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldGreen.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "DEFAULT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${vehicleType.displayName} • ${fuelType.displayName}",
                            fontSize = 12.sp,
                            color = SlateLight
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit vehicle",
                            tint = BlueCobalt,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete vehicle",
                            tint = SlateLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats row: Efficiency & Maintenance Wear rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Fuel Mileage", fontSize = 10.sp, color = SlateLight)
                        Text(
                            text = "${vehicle.efficiencyVal} ${vehicleType.efficiencyUnit}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Wear & Tear Rate", fontSize = 10.sp, color = SlateLight)
                        Text(
                            text = "${settings.currencySymbol}${vehicle.maintenanceCostPerKm}/${settings.unitDistance}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    }
                }
            }

            if (!vehicle.isDefault) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSetDefault)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = AmberPersonal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as Default Vehicle",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = NavyDark
                    )
                }
            }
        }
    }
}

@Composable
fun EditVehicleDialog(
    vehicle: VehicleEntity,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onConfirm: (VehicleEntity) -> Unit
) {
    var name by remember { mutableStateOf(vehicle.name) }
    var selectedType by remember { mutableStateOf(vehicle.getParsedVehicleType()) }
    var selectedFuel by remember { mutableStateOf(vehicle.getParsedFuelType()) }
    var efficiencyText by remember { mutableStateOf(vehicle.efficiencyVal.toString()) }
    var wearText by remember { mutableStateOf(vehicle.maintenanceCostPerKm.toString()) }
    var isDefault by remember { mutableStateOf(vehicle.isDefault) }

    var typeDropdownOpen by remember { mutableStateOf(false) }
    var fuelDropdownOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Edit Vehicle Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Text(
                    text = "Updating efficiency or wear rate will recalculate all completed trips for this vehicle",
                    fontSize = 11.sp,
                    color = BlueCobalt,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vehicle Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Vehicle Type Dropdown
                Text("Vehicle Type", fontSize = 12.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { typeDropdownOpen = true },
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = selectedType.displayName,
                            fontSize = 14.sp,
                            color = NavyDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = typeDropdownOpen,
                        onDismissRequest = { typeDropdownOpen = false }
                    ) {
                        VehicleType.entries.forEach { vType ->
                            DropdownMenuItem(
                                text = { Text(vType.displayName) },
                                onClick = {
                                    selectedType = vType
                                    typeDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fuel Type Dropdown
                Text("Fuel Type", fontSize = 12.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { fuelDropdownOpen = true },
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = selectedFuel.displayName,
                            fontSize = 14.sp,
                            color = NavyDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = fuelDropdownOpen,
                        onDismissRequest = { fuelDropdownOpen = false }
                    ) {
                        FuelType.entries.forEach { fType ->
                            DropdownMenuItem(
                                text = { Text(fType.displayName) },
                                onClick = {
                                    selectedFuel = fType
                                    fuelDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mileage Efficiency input
                OutlinedTextField(
                    value = efficiencyText,
                    onValueChange = { efficiencyText = it },
                    label = { Text("Fuel Mileage Efficiency (${selectedType.efficiencyUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wear and Tear rate input
                OutlinedTextField(
                    value = wearText,
                    onValueChange = { wearText = it },
                    label = { Text("Wear & Tear Maintenance Cost (${settings.currencySymbol}/${settings.unitDistance})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Default vehicle checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDefault = !isDefault },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as Primary Default Vehicle",
                        fontSize = 13.sp,
                        color = NavyDark
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = SlateDark)
                    }
                    Button(
                        onClick = {
                            val eff = efficiencyText.toDoubleOrNull() ?: selectedType.defaultEfficiency
                            val wear = wearText.toDoubleOrNull() ?: 0.50
                            onConfirm(
                                vehicle.copy(
                                    name = name.ifBlank { vehicle.name },
                                    vehicleType = selectedType.name,
                                    fuelType = selectedFuel.name,
                                    efficiencyVal = eff,
                                    maintenanceCostPerKm = wear,
                                    isDefault = isDefault
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save & Recalculate", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AddVehicleDialog(
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: VehicleType, fuel: FuelType, efficiency: Double, wear: Double, isDefault: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(VehicleType.CAR) }
    var selectedFuel by remember { mutableStateOf(FuelType.PETROL) }
    var efficiencyText by remember { mutableStateOf("14.0") }
    var wearText by remember { mutableStateOf("0.50") }
    var isDefault by remember { mutableStateOf(false) }

    var typeDropdownOpen by remember { mutableStateOf(false) }
    var fuelDropdownOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add New Vehicle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark
                )
                Text(
                    text = "Configure vehicle specifications and fuel rate",
                    fontSize = 11.sp,
                    color = SlateLight
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Vehicle Name (e.g. Royal Enfield 350 / Honda City)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Vehicle Type Dropdown
                Text("Vehicle Type", fontSize = 12.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { typeDropdownOpen = true },
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = selectedType.displayName,
                            fontSize = 14.sp,
                            color = NavyDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = typeDropdownOpen,
                        onDismissRequest = { typeDropdownOpen = false }
                    ) {
                        VehicleType.entries.forEach { vType ->
                            DropdownMenuItem(
                                text = { Text(vType.displayName) },
                                onClick = {
                                    selectedType = vType
                                    efficiencyText = vType.defaultEfficiency.toString()
                                    typeDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fuel Type Dropdown
                Text("Fuel Type", fontSize = 12.sp, color = SlateLight, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { fuelDropdownOpen = true },
                        color = Color(0xFFF1F5F9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                    ) {
                        Text(
                            text = selectedFuel.displayName,
                            fontSize = 14.sp,
                            color = NavyDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = fuelDropdownOpen,
                        onDismissRequest = { fuelDropdownOpen = false }
                    ) {
                        FuelType.entries.forEach { fType ->
                            DropdownMenuItem(
                                text = { Text(fType.displayName) },
                                onClick = {
                                    selectedFuel = fType
                                    fuelDropdownOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mileage Efficiency input
                OutlinedTextField(
                    value = efficiencyText,
                    onValueChange = { efficiencyText = it },
                    label = { Text("Fuel Mileage Efficiency (${selectedType.efficiencyUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wear and Tear rate input
                OutlinedTextField(
                    value = wearText,
                    onValueChange = { wearText = it },
                    label = { Text("Wear & Tear Maintenance Cost (${settings.currencySymbol}/${settings.unitDistance})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Default vehicle checkbox
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDefault = !isDefault },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = isDefault, onCheckedChange = { isDefault = it })
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set as Primary Default Vehicle",
                        fontSize = 13.sp,
                        color = NavyDark
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = SlateDark)
                    }
                    Button(
                        onClick = {
                            val eff = efficiencyText.toDoubleOrNull() ?: selectedType.defaultEfficiency
                            val wear = wearText.toDoubleOrNull() ?: 0.50
                            onConfirm(name.ifBlank { "My ${selectedType.displayName}" }, selectedType, selectedFuel, eff, wear, isDefault)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueCobalt),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Vehicle", color = Color.White)
                    }
                }
            }
        }
    }
}
