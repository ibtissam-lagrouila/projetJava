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

export interface CandidateScoreBreakdown {
  matchedSkills: string[];
  missingSkills: string[];
  skillsScore: number;
  interestScore: number;
  datesScore: number;
  matchLabel: string;
  explanation: string;
}

export interface TopCandidateItem {
  applicationId: string;
  studentId: string;
  studentName: string;
  initials: string;
  educationLevel: string;
  fieldOfStudy: string;
  offerId: string;
  offerTitle: string;
  matchPercentage: number | null;
  isLimitedData: boolean;
  scoreBreakdown: CandidateScoreBreakdown;
  appliedAt: string;
  status: string;
}

export interface SuggestionItem {
  id: string;
  type: string;
  title: string;
  description: string;
  actionLabel: string;
  actionType: string;
  targetOfferId?: string;
  candidateIds?: string[];
  candidateNames?: string[];
  targetUrl?: string;
}

export interface ThisMonthActivity {
  views: number;
  applied: number;
  shortlisted: number;
  interviews: number;
  isAllZero: boolean;
  viewsPct: number;
  appliedPct: number;
  shortlistedPct: number;
  interviewsPct: number;
}

export interface DashboardCounters {
  openOffers: number;
  newApplicants: number;
  toReview: number;
  interviewsThisWeek: number;
  awaitingConfirmation: number;
}

export interface AvailableLabItem {
  labId: string;
  labName: string;
  city: string;
  universityEntity: string;
  role: string;
  status: string;
}

export interface LabDashboardData {
  greeting: string;
  accountName: string;
  labName: string;
  city: string;
  role: string;
  activeLabId: string;
  status: string;
  availableLabs: AvailableLabItem[];
  counters: DashboardCounters;
  topCandidates: TopCandidateItem[];
  suggestions: SuggestionItem[];
  thisMonth: ThisMonthActivity;
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

export interface KanbanCandidateItem {
  id: string;
  studentId: string;
  offerId: string;
  offerTitle: string;
  studentName: string;
  level: string;
  fieldOfStudy: string;
  university: string;
  skills: string[];
  matchScore: number;
  status: string;
  appliedAt: string;
  lastActivityAt: string;
  coverLetter?: string;
}

export interface LabOfferSummary {
  offerId: string;
  title: string;
  candidateCount: number;
}

export interface KanbanColumns {
  new: KanbanCandidateItem[];
  shortlisted: KanbanCandidateItem[];
  interview: KanbanCandidateItem[];
  decision: KanbanCandidateItem[];
}

export interface KanbanCounts {
  new: number;
  shortlisted: number;
  interview: number;
  decision: number;
  total: number;
}

export interface LabCandidatesResponse {
  selectedOfferId: string | null;
  selectedOfferTitle: string;
  offers: LabOfferSummary[];
  columns: KanbanColumns;
  counts: KanbanCounts;
}


