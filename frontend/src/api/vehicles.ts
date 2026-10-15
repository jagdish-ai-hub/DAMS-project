import api from './axios'

export interface Vehicle {
  id: number
  vehicleNo: string
  customerId: number
  customerName: string | null
  createdAt: string
}

export interface VehicleRequest {
  customerId: number
  vehicleNo: string
}

export const vehiclesApi = {
  byNumber(vehicleNo: string) {
    return api.get<Vehicle>('/api/v1/vehicles', { params: { vehicle_no: vehicleNo } })
  },
  /** Owner (rev 77) — correct a vehicle's number; refused if another vehicle already holds it. */
  rename(id: number, vehicleNo: string) {
    return api.patch<Vehicle>(`/api/v1/vehicles/${id}`, { vehicleNo })
  },
  create(data: VehicleRequest) {
    return api.post<Vehicle>('/api/v1/vehicles', data)
  },
}
