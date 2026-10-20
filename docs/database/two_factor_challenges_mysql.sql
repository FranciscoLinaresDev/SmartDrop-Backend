
-- SmartDrop - Two-factor authentication challenges
-- Draft migration for MySQL
-- Do not execute against production without schema review.

CREATE TABLE TwoFactorChallenges (
                                     ChallengeId VARCHAR(36) NOT NULL,
                                     UserId INT NOT NULL,
                                     CodeHash VARCHAR(64) NOT NULL,
                                     CreatedAt DATETIME(6) NOT NULL,
                                     ExpiresAt DATETIME(6) NOT NULL,
                                     FailedAttempts INT NOT NULL DEFAULT 0,
                                     ConsumedAt DATETIME(6) NULL,
                                     RowVersion BIGINT NOT NULL DEFAULT 0,

                                     CONSTRAINT PK_TwoFactorChallenges
                                         PRIMARY KEY (ChallengeId),

                                     CONSTRAINT FK_TwoFactorChallenges_Users
                                         FOREIGN KEY (UserId)
                                             REFERENCES Users(UserId)
);

CREATE INDEX IX_TwoFactorChallenges_UserId
    ON TwoFactorChallenges(UserId);

CREATE INDEX IX_TwoFactorChallenges_ExpiresAt
    ON TwoFactorChallenges(ExpiresAt);
