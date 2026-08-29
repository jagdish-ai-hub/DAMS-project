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
  create(data: VehicleRequest) {
    return api.post<Vehicle>('/api/v1/vehicles', data)
  },
}
