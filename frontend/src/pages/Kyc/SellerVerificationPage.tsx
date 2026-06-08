import { useEffect, useRef, useState } from 'react';
import { kycService } from '../../services/kycService';
import { useAppSelector } from '../../store';
import { Loading } from '../../components/common';
import { CameraCapture } from './CameraCapture';
import type {
  IdDocumentType,
  KycCase,
  KycDocType,
  SellerProfile,
  SellerType,
} from '../../types';

const PRIVACY_NOTICE =
  'Your documents are used only for verification. They are deleted automatically when a decision is made, or after 72 hours at the latest. Only the verification result is kept.';

const POLL_INTERVAL_MS = 5000;

/** Maps a thrown api error to whether it represents a 404 (not found). */
const isNotFound = (e: unknown): boolean =>
  e instanceof Error && /404|not\s+found/i.test(e.message);

const errorMessage = (e: unknown, fallback: string): string =>
  e instanceof Error ? e.message : fallback;

type WizardStep = 'details' | 'photoId' | 'selfies' | 'addressProof' | 'review';

const STEP_ORDER: WizardStep[] = [
  'details',
  'photoId',
  'selfies',
  'addressProof',
  'review',
];

const STEP_LABELS: Record<WizardStep, string> = {
  details: 'Your details',
  photoId: 'Photo ID',
  selfies: 'Selfies',
  addressProof: 'Address proof',
  review: 'Review & submit',
};

const ID_DOC_TYPES: { value: IdDocumentType; label: string }[] = [
  { value: 'NATIONAL_ID', label: 'National ID card' },
  { value: 'PASSPORT', label: 'Passport' },
  { value: 'DRIVING_LICENSE', label: "Driver's license" },
];

const SELFIE_SLOTS: { docType: KycDocType; label: string; instruction: string }[] = [
  {
    docType: 'SELFIE_FRONT',
    label: 'Front selfie',
    instruction: 'Look straight at the camera',
  },
  {
    docType: 'SELFIE_LEFT',
    label: 'Left turn',
    instruction: 'Turn your head slightly LEFT',
  },
  {
    docType: 'SELFIE_RIGHT',
    label: 'Right turn',
    instruction: 'Turn your head slightly RIGHT',
  },
];

const emptyProfile: SellerProfile = {
  sellerType: 'INDIVIDUAL',
  legalName: '',
  dateOfBirth: '',
  phone: '',
  idDocumentType: 'NATIONAL_ID',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  countryCode: '',
};

const isActiveStatus = (c: KycCase): boolean =>
  c.status === 'SUBMITTED' || c.status === 'CHECKING';

const isDecidedView = (c: KycCase): boolean =>
  c.status === 'SUBMITTED' ||
  c.status === 'CHECKING' ||
  c.status === 'IN_REVIEW' ||
  c.status === 'APPROVED' ||
  c.status === 'REJECTED' ||
  c.status === 'EXPIRED';

const hasDoc = (c: KycCase | null, docType: KycDocType): boolean =>
  !!c && c.documents.some((d) => d.docType === docType);

export function SellerVerificationPage() {
  const user = useAppSelector((state) => state.auth.user);

  // Top-level page state
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // The active case (drives both the wizard and the status view).
  const [kycCase, setKycCase] = useState<KycCase | null>(null);
  // When true, we show the decided/in-progress status view instead of the wizard.
  const [showStatus, setShowStatus] = useState(false);

  // Wizard navigation
  const [step, setStep] = useState<WizardStep>('details');

  // Profile form
  const [profile, setProfile] = useState<SellerProfile>(emptyProfile);

  // Per-action busy / error
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  // Upload tracking — local object URLs for previews, keyed by slot.
  const [previews, setPreviews] = useState<Partial<Record<KycDocType, string>>>({});
  const [uploadingSlot, setUploadingSlot] = useState<KycDocType | null>(null);

  const pollRef = useRef<number | null>(null);

  const clearPoll = () => {
    if (pollRef.current !== null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const stopPollIfDecided = (c: KycCase) => {
    if (!isActiveStatus(c)) clearPoll();
  };

  const startPolling = () => {
    clearPoll();
    pollRef.current = window.setInterval(() => {
      kycService
        .getCurrentCase()
        .then((c) => {
          setKycCase(c);
          stopPollIfDecided(c);
        })
        .catch(() => {
          /* transient poll failure — keep trying until a decision lands */
        });
    }, POLL_INTERVAL_MS);
  };

  // ---- Initial load ----
  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setIsLoading(true);
      setLoadError(null);

      // Already a verified seller — straight to the approved panel.
      if (user?.idVerified) {
        if (!cancelled) {
          setKycCase(null);
          setShowStatus(true);
          setIsLoading(false);
        }
        return;
      }

      try {
        const current = await kycService.getCurrentCase();
        if (cancelled) return;
        setKycCase(current);

        if (isDecidedView(current) && current.status !== 'DRAFT') {
          setShowStatus(true);
          if (isActiveStatus(current)) startPolling();
        } else {
          // DRAFT case — resume the wizard. Prefill the profile if it exists.
          await prefillProfile(cancelled);
          setShowStatus(false);
          setStep('photoId');
        }
      } catch (e) {
        if (cancelled) return;
        if (isNotFound(e)) {
          // No case yet — fresh wizard. Try to prefill an existing profile.
          await prefillProfile(cancelled);
          setShowStatus(false);
          setStep('details');
        } else {
          setLoadError(errorMessage(e, 'Could not load your verification status.'));
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    const prefillProfile = async (isCancelled: boolean) => {
      try {
        const existing = await kycService.getProfile();
        if (!isCancelled) {
          setProfile({ ...emptyProfile, ...existing });
        }
      } catch (e) {
        if (!isNotFound(e)) {
          // Non-404 profile errors are non-fatal here; user can re-enter details.
        }
      }
    };

    load();

    return () => {
      cancelled = true;
      clearPoll();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.idVerified]);

  // Revoke any local preview URLs on unmount.
  useEffect(() => {
    return () => {
      Object.values(previews).forEach((url) => {
        if (url) URL.revokeObjectURL(url);
      });
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const idBackRequired = profile.idDocumentType !== 'PASSPORT';

  const updateProfile = <K extends keyof SellerProfile>(
    key: K,
    value: SellerProfile[K]
  ) => {
    setProfile((prev) => ({ ...prev, [key]: value }));
  };

  // ---- Step a: details → upsert profile + open case ----
  const submitDetails = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionError(null);

    if (!profile.legalName.trim()) {
      setActionError('Please enter your legal name exactly as printed on your ID.');
      return;
    }
    if (!profile.addressLine1.trim() || !profile.city.trim()) {
      setActionError('Please complete your address.');
      return;
    }
    if (profile.countryCode.trim().length !== 2) {
      setActionError('Country code must be a 2-letter code, e.g. US or GB.');
      return;
    }

    setBusy(true);
    try {
      await kycService.upsertProfile({
        ...profile,
        countryCode: profile.countryCode.trim().toUpperCase(),
      });
      const opened = await kycService.openCase();
      setKycCase(opened);
      setStep('photoId');
    } catch (err) {
      setActionError(errorMessage(err, 'Could not save your details. Please try again.'));
    } finally {
      setBusy(false);
    }
  };

  // ---- Document upload helper (file inputs and camera blobs) ----
  const setPreview = (docType: KycDocType, url: string) => {
    setPreviews((prev) => {
      const old = prev[docType];
      if (old) URL.revokeObjectURL(old);
      return { ...prev, [docType]: url };
    });
  };

  const uploadSlot = async (docType: KycDocType, blob: File | Blob) => {
    if (!kycCase) return;
    setActionError(null);
    setUploadingSlot(docType);
    // Optimistic local preview.
    setPreview(docType, URL.createObjectURL(blob));
    try {
      await kycService.uploadDocument(kycCase.id, docType, blob);
      // Refresh the case so the review checklist reflects server state.
      const refreshed = await kycService.getCurrentCase();
      setKycCase(refreshed);
    } catch (err) {
      setActionError(errorMessage(err, 'Upload failed. Please try again.'));
    } finally {
      setUploadingSlot(null);
    }
  };

  const onFileSelected = (
    docType: KycDocType,
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
      setActionError('Please upload a JPEG, PNG or WEBP image.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setActionError('Image must be 10MB or smaller.');
      return;
    }
    void uploadSlot(docType, file);
  };

  // ---- Step e: submit ----
  const submitCase = async () => {
    if (!kycCase) return;
    setActionError(null);
    setBusy(true);
    try {
      const submitted = await kycService.submit(kycCase.id);
      setKycCase(submitted);
      setShowStatus(true);
      if (isActiveStatus(submitted)) startPolling();
    } catch (err) {
      setActionError(errorMessage(err, 'Could not submit. Please check your uploads.'));
    } finally {
      setBusy(false);
    }
  };

  // ---- Start again (rejected / expired) ----
  const startAgain = async () => {
    setActionError(null);
    setBusy(true);
    try {
      const opened = await kycService.openCase();
      setKycCase(opened);
      setPreviews({});
      setShowStatus(false);
      setStep('photoId');
    } catch (err) {
      setActionError(errorMessage(err, 'Could not start a new verification.'));
    } finally {
      setBusy(false);
    }
  };

  // ---- Render guards ----
  if (isLoading) {
    return (
      <div className="container py-5">
        <Loading message="Loading verification…" />
      </div>
    );
  }

  if (loadError) {
    return (
      <div className="container py-5">
        <div className="alert alert-danger">{loadError}</div>
      </div>
    );
  }

  // ---- Status view ----
  if (showStatus) {
    return (
      <div className="container py-5">
        <h1 className="h4 mb-4">Seller verification</h1>
        <StatusView
          kycCase={kycCase}
          isApprovedUser={!!user?.idVerified}
          onStartAgain={startAgain}
          busy={busy}
          actionError={actionError}
        />
      </div>
    );
  }

  // ---- Wizard ----
  const currentStepIndex = STEP_ORDER.indexOf(step);

  return (
    <div className="container py-5">
      <h1 className="h4 mb-1">Become a verified seller</h1>
      <p className="text-muted">
        Complete the steps below to verify your identity and start selling.
      </p>

      {/* Progress indicator — equal segments, completed ones filled. Pure
          Bootstrap utilities (no inline width) so it stays rule-compliant. */}
      <div className="mb-4">
        <div
          className="d-flex gap-1 mb-2"
          role="progressbar"
          aria-label="Verification progress"
          aria-valuenow={currentStepIndex + 1}
          aria-valuemin={1}
          aria-valuemax={STEP_ORDER.length}
        >
          {STEP_ORDER.map((s, i) => (
            <div
              key={s}
              className={`progress flex-fill ${
                i <= currentStepIndex ? '' : 'bg-light'
              }`}
            >
              <div
                className={`progress-bar w-100 ${
                  i <= currentStepIndex ? '' : 'opacity-0'
                }`}
              ></div>
            </div>
          ))}
        </div>
        <div className="d-flex justify-content-between flex-wrap gap-1">
          <span className="small fw-semibold text-primary">
            {STEP_LABELS[step]}
          </span>
          <span className="small text-muted">
            Step {currentStepIndex + 1} of {STEP_ORDER.length}
          </span>
        </div>
      </div>

      {actionError && <div className="alert alert-danger">{actionError}</div>}

      {/* Step a — details */}
      {step === 'details' && (
        <form onSubmit={submitDetails} noValidate>
          <div className="card">
            <div className="card-body">
              <h5 className="card-title mb-3">Your details</h5>

              <div className="mb-3">
                <span className="form-label d-block">I am…</span>
                <div className="form-check">
                  <input
                    className="form-check-input"
                    type="radio"
                    name="sellerType"
                    id="sellerTypeBusiness"
                    checked={profile.sellerType === 'BUSINESS'}
                    onChange={() => updateProfile('sellerType', 'BUSINESS' as SellerType)}
                  />
                  <label className="form-check-label" htmlFor="sellerTypeBusiness">
                    I run a business
                  </label>
                </div>
                <div className="form-check">
                  <input
                    className="form-check-input"
                    type="radio"
                    name="sellerType"
                    id="sellerTypeIndividual"
                    checked={profile.sellerType === 'INDIVIDUAL'}
                    onChange={() =>
                      updateProfile('sellerType', 'INDIVIDUAL' as SellerType)
                    }
                  />
                  <label className="form-check-label" htmlFor="sellerTypeIndividual">
                    I'm selling my own items
                  </label>
                </div>
              </div>

              <div className="row g-3">
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="legalName">
                    Legal name
                  </label>
                  <input
                    id="legalName"
                    type="text"
                    className="form-control"
                    value={profile.legalName}
                    onChange={(e) => updateProfile('legalName', e.target.value)}
                    required
                  />
                  <div className="form-text">Exactly as printed on your ID.</div>
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="dateOfBirth">
                    Date of birth
                  </label>
                  <input
                    id="dateOfBirth"
                    type="date"
                    className="form-control"
                    value={profile.dateOfBirth ?? ''}
                    onChange={(e) => updateProfile('dateOfBirth', e.target.value)}
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="phone">
                    Phone
                  </label>
                  <input
                    id="phone"
                    type="tel"
                    className="form-control"
                    value={profile.phone ?? ''}
                    onChange={(e) => updateProfile('phone', e.target.value)}
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="idDocumentType">
                    ID document type
                  </label>
                  <select
                    id="idDocumentType"
                    className="form-select"
                    value={profile.idDocumentType}
                    onChange={(e) =>
                      updateProfile('idDocumentType', e.target.value as IdDocumentType)
                    }
                  >
                    {ID_DOC_TYPES.map((opt) => (
                      <option key={opt.value} value={opt.value}>
                        {opt.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="col-12">
                  <label className="form-label" htmlFor="addressLine1">
                    Address line 1
                  </label>
                  <input
                    id="addressLine1"
                    type="text"
                    className="form-control"
                    value={profile.addressLine1}
                    onChange={(e) => updateProfile('addressLine1', e.target.value)}
                    required
                  />
                </div>
                <div className="col-12">
                  <label className="form-label" htmlFor="addressLine2">
                    Address line 2 <span className="text-muted">(optional)</span>
                  </label>
                  <input
                    id="addressLine2"
                    type="text"
                    className="form-control"
                    value={profile.addressLine2 ?? ''}
                    onChange={(e) => updateProfile('addressLine2', e.target.value)}
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="city">
                    City
                  </label>
                  <input
                    id="city"
                    type="text"
                    className="form-control"
                    value={profile.city}
                    onChange={(e) => updateProfile('city', e.target.value)}
                    required
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="state">
                    State / region <span className="text-muted">(optional)</span>
                  </label>
                  <input
                    id="state"
                    type="text"
                    className="form-control"
                    value={profile.state ?? ''}
                    onChange={(e) => updateProfile('state', e.target.value)}
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="postalCode">
                    Postal code <span className="text-muted">(optional)</span>
                  </label>
                  <input
                    id="postalCode"
                    type="text"
                    className="form-control"
                    value={profile.postalCode ?? ''}
                    onChange={(e) => updateProfile('postalCode', e.target.value)}
                  />
                </div>
                <div className="col-12 col-md-6">
                  <label className="form-label" htmlFor="countryCode">
                    Country code
                  </label>
                  <input
                    id="countryCode"
                    type="text"
                    className="form-control text-uppercase"
                    maxLength={2}
                    placeholder="US"
                    value={profile.countryCode}
                    onChange={(e) => updateProfile('countryCode', e.target.value)}
                    required
                  />
                  <div className="form-text">2-letter country code.</div>
                </div>
              </div>
            </div>
            <div className="card-footer d-flex justify-content-end">
              <button type="submit" className="btn btn-primary" disabled={busy}>
                {busy ? 'Saving…' : 'Save & continue'}
              </button>
            </div>
          </div>
        </form>
      )}

      {/* Step b — photo ID */}
      {step === 'photoId' && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">Photo ID</h5>
            <p className="text-muted small">
              Upload clear photos of your {ID_DOC_TYPES.find(
                (t) => t.value === profile.idDocumentType
              )?.label ?? 'ID'}
              . JPEG, PNG or WEBP, up to 10MB.
            </p>

            <div className="row g-4">
              <div className="col-12 col-md-6">
                <DocUploadSlot
                  label="Front of ID"
                  docType="ID_FRONT"
                  previewUrl={previews.ID_FRONT}
                  uploaded={hasDoc(kycCase, 'ID_FRONT')}
                  uploading={uploadingSlot === 'ID_FRONT'}
                  onFileSelected={onFileSelected}
                />
              </div>
              {idBackRequired && (
                <div className="col-12 col-md-6">
                  <DocUploadSlot
                    label="Back of ID"
                    docType="ID_BACK"
                    previewUrl={previews.ID_BACK}
                    uploaded={hasDoc(kycCase, 'ID_BACK')}
                    uploading={uploadingSlot === 'ID_BACK'}
                    onFileSelected={onFileSelected}
                  />
                </div>
              )}
            </div>
          </div>
          <div className="card-footer d-flex justify-content-end">
            <button
              type="button"
              className="btn btn-primary"
              disabled={
                !hasDoc(kycCase, 'ID_FRONT') ||
                (idBackRequired && !hasDoc(kycCase, 'ID_BACK'))
              }
              onClick={() => setStep('selfies')}
            >
              Continue
            </button>
          </div>
        </div>
      )}

      {/* Step c — selfies */}
      {step === 'selfies' && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">Selfies</h5>
            <p className="text-muted small">
              We compare these with your ID photo. Make sure your face is well lit and
              unobstructed.
            </p>
            <div className="row g-4">
              {SELFIE_SLOTS.map((slot) => (
                <div key={slot.docType} className="col-12 col-md-4">
                  <CameraCapture
                    label={slot.label}
                    instruction={slot.instruction}
                    capturedPreviewUrl={previews[slot.docType] ?? null}
                    onCapture={(blob) => void uploadSlot(slot.docType, blob)}
                  />
                  <div className="mt-2 text-center small">
                    {uploadingSlot === slot.docType ? (
                      <span className="text-muted">Uploading…</span>
                    ) : hasDoc(kycCase, slot.docType) ? (
                      <span className="text-success">
                        <i className="bi bi-check-circle me-1"></i>Captured
                      </span>
                    ) : (
                      <span className="text-muted">Not captured yet</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="card-footer d-flex justify-content-between">
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={() => setStep('photoId')}
            >
              Back
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={
                !hasDoc(kycCase, 'SELFIE_FRONT') ||
                !hasDoc(kycCase, 'SELFIE_LEFT') ||
                !hasDoc(kycCase, 'SELFIE_RIGHT')
              }
              onClick={() => setStep('addressProof')}
            >
              Continue
            </button>
          </div>
        </div>
      )}

      {/* Step d — address proof */}
      {step === 'addressProof' && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">Address proof</h5>
            <p className="text-muted small">
              Upload an electricity bill clearly showing the same address you entered.
            </p>
            <div className="row g-4">
              <div className="col-12 col-md-6">
                <DocUploadSlot
                  label="Utility bill"
                  docType="UTILITY_BILL"
                  previewUrl={previews.UTILITY_BILL}
                  uploaded={hasDoc(kycCase, 'UTILITY_BILL')}
                  uploading={uploadingSlot === 'UTILITY_BILL'}
                  onFileSelected={onFileSelected}
                />
              </div>
            </div>
          </div>
          <div className="card-footer d-flex justify-content-between">
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={() => setStep('selfies')}
            >
              Back
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={!hasDoc(kycCase, 'UTILITY_BILL')}
              onClick={() => setStep('review')}
            >
              Continue
            </button>
          </div>
        </div>
      )}

      {/* Step e — review & submit */}
      {step === 'review' && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">Review & submit</h5>

            <ul className="list-group mb-4">
              <ReviewRow label="Front of ID" done={hasDoc(kycCase, 'ID_FRONT')} />
              {idBackRequired && (
                <ReviewRow label="Back of ID" done={hasDoc(kycCase, 'ID_BACK')} />
              )}
              <ReviewRow label="Front selfie" done={hasDoc(kycCase, 'SELFIE_FRONT')} />
              <ReviewRow label="Left-turn selfie" done={hasDoc(kycCase, 'SELFIE_LEFT')} />
              <ReviewRow label="Right-turn selfie" done={hasDoc(kycCase, 'SELFIE_RIGHT')} />
              <ReviewRow label="Utility bill" done={hasDoc(kycCase, 'UTILITY_BILL')} />
            </ul>

            <div className="alert alert-info small mb-0">
              <i className="bi bi-shield-lock me-1"></i>
              {PRIVACY_NOTICE}
            </div>
          </div>
          <div className="card-footer d-flex justify-content-between">
            <button
              type="button"
              className="btn btn-outline-secondary"
              onClick={() => setStep('addressProof')}
            >
              Back
            </button>
            <button
              type="button"
              className="btn btn-success"
              onClick={submitCase}
              disabled={busy || !allUploaded(kycCase, idBackRequired)}
            >
              {busy ? 'Submitting…' : 'Submit for verification'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const allUploaded = (c: KycCase | null, idBackRequired: boolean): boolean => {
  if (!c) return false;
  const required: KycDocType[] = [
    'ID_FRONT',
    'SELFIE_FRONT',
    'SELFIE_LEFT',
    'SELFIE_RIGHT',
    'UTILITY_BILL',
  ];
  if (idBackRequired) required.push('ID_BACK');
  return required.every((d) => c.documents.some((doc) => doc.docType === d));
};

interface DocUploadSlotProps {
  label: string;
  docType: KycDocType;
  previewUrl?: string;
  uploaded: boolean;
  uploading: boolean;
  onFileSelected: (docType: KycDocType, e: React.ChangeEvent<HTMLInputElement>) => void;
}

function DocUploadSlot({
  label,
  docType,
  previewUrl,
  uploaded,
  uploading,
  onFileSelected,
}: DocUploadSlotProps) {
  const inputId = `upload-${docType}`;
  return (
    <div className="border rounded p-3 h-100">
      <div className="d-flex justify-content-between align-items-center mb-2">
        <span className="fw-semibold">{label}</span>
        {uploaded && (
          <span className="badge bg-success">
            <i className="bi bi-check-lg me-1"></i>Uploaded
          </span>
        )}
      </div>

      {previewUrl && (
        <div className="ratio ratio-4x3 bg-light rounded mb-2 overflow-hidden">
          <img
            src={previewUrl}
            alt={`${label} preview`}
            className="w-100 h-100 object-fit-contain"
          />
        </div>
      )}

      <label className="form-label" htmlFor={inputId}>
        {uploaded || previewUrl ? 'Replace photo' : 'Choose photo'}
      </label>
      <input
        id={inputId}
        type="file"
        className="form-control"
        accept="image/jpeg,image/png,image/webp"
        disabled={uploading}
        onChange={(e) => onFileSelected(docType, e)}
      />
      {uploading && <div className="form-text text-muted">Uploading…</div>}
    </div>
  );
}

interface ReviewRowProps {
  label: string;
  done: boolean;
}

function ReviewRow({ label, done }: ReviewRowProps) {
  return (
    <li className="list-group-item d-flex justify-content-between align-items-center">
      <span>{label}</span>
      {done ? (
        <span className="text-success">
          <i className="bi bi-check-circle-fill me-1"></i>Ready
        </span>
      ) : (
        <span className="text-danger">
          <i className="bi bi-exclamation-circle me-1"></i>Missing
        </span>
      )}
    </li>
  );
}

interface StatusViewProps {
  kycCase: KycCase | null;
  isApprovedUser: boolean;
  onStartAgain: () => void;
  busy: boolean;
  actionError: string | null;
}

function StatusView({
  kycCase,
  isApprovedUser,
  onStartAgain,
  busy,
  actionError,
}: StatusViewProps) {
  // A verified user with no fresh case still sees the approved panel.
  const status = isApprovedUser && !kycCase ? 'APPROVED' : kycCase?.status;

  if (status === 'APPROVED') {
    return (
      <div className="card border-success">
        <div className="card-body text-center py-5">
          <div className="display-4 text-success mb-3">
            <i className="bi bi-patch-check-fill"></i>
          </div>
          <h2 className="h4 mb-2">
            You're a verified seller{' '}
            <span className="badge bg-success align-middle">Verified</span>
          </h2>
          <p className="text-muted mb-0">
            Your identity has been verified. You can now list items for sale.
          </p>
        </div>
      </div>
    );
  }

  if (status === 'SUBMITTED' || status === 'CHECKING') {
    return (
      <div className="card">
        <div className="card-body text-center py-5">
          <div className="spinner-border text-primary mb-3" role="status">
            <span className="visually-hidden">Running automated checks…</span>
          </div>
          <h2 className="h5 mb-2">Running automated checks…</h2>
          <p className="text-muted mb-0">
            This usually takes a few minutes. You can leave this page — we'll keep checking.
          </p>
        </div>
      </div>
    );
  }

  if (status === 'IN_REVIEW') {
    return (
      <div className="alert alert-info">
        <h2 className="h5 alert-heading">A reviewer will check your submission</h2>
        <p className="mb-0">
          Your automated checks need a closer look. A member of our team will review your
          submission shortly.
        </p>
        {kycCase?.expiresAt && (
          <p className="mb-0 mt-2 small text-muted">
            Your documents will be deleted by{' '}
            {new Date(kycCase.expiresAt).toLocaleString()}.
          </p>
        )}
      </div>
    );
  }

  if (status === 'REJECTED') {
    return (
      <div>
        <div className="alert alert-danger">
          <h2 className="h5 alert-heading">Verification was not approved</h2>
          <p className="mb-0">
            {kycCase?.rejectionReason ??
              'We could not verify your identity from the documents provided.'}
          </p>
        </div>
        {actionError && <div className="alert alert-danger">{actionError}</div>}
        <button
          type="button"
          className="btn btn-primary"
          onClick={onStartAgain}
          disabled={busy}
        >
          {busy ? 'Starting…' : 'Start again'}
        </button>
      </div>
    );
  }

  if (status === 'EXPIRED') {
    return (
      <div>
        <div className="alert alert-warning">
          <h2 className="h5 alert-heading">Your verification expired</h2>
          <p className="mb-0">
            Your documents were deleted after the 72-hour retention period. Please start a
            new verification.
          </p>
        </div>
        {actionError && <div className="alert alert-danger">{actionError}</div>}
        <button
          type="button"
          className="btn btn-primary"
          onClick={onStartAgain}
          disabled={busy}
        >
          {busy ? 'Starting…' : 'Start again'}
        </button>
      </div>
    );
  }

  // Fallback (e.g. unexpected DRAFT in status view).
  return (
    <div className="alert alert-secondary mb-0">
      Your verification status is being updated. Please refresh in a moment.
    </div>
  );
}
