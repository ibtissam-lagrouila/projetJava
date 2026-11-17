export type Role = 'STUDENT' | 'LAB' | 'ADMIN';

export interface User {
  id: string;
  email: string;
  role: Role;
  emailVerified: boolean;
  fullName: string;
  level?: string;
  labId?: string;
  isLabApproved?: boolean;
  onboardingCompleted?: boolean;
  isVerifiedStudent?: boolean;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Badges {
  applications?: number;
  messages?: number;
  candidates?: number;
  interviews?: number;
  validation?: number;
  claims?: number;
  [key: string]: number | undefined;
}

export const MOROCCAN_UNIVERSITIES = [
  'Université Mohammed VI Polytechnique (UM6P)',
  'Université Mohammed V de Rabat (UM5)',
  'Université Hassan II de Casablanca (UH2C)',
  'Université Cadi Ayyad de Marrakech (UCA)',
  'Université Sidi Mohamed Ben Abdellah de Fès (USMBA)',
  'Université Abdelmalek Essaâdi de Tétouan (UAE)',
  'Université Ibn Zohr d\'Agadir (UIZ)',
  'Université Ibn Tofail de Kénitra (UIT)',
  'Université Mohammed Premier d\'Oujda (UMP)',
  'Université Moulay Ismaïl de Meknès (UMI)',
  'Université Al Akhawayn d\'Ifrane (AUI)',
  'Université Chouaib Doukkali d\'El Jadida (UCD)',
  'Université Sultan Moulay Slimane de Béni Mellal (USMS)',
];

