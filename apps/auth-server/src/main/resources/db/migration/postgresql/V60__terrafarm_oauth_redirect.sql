-- TerraFarm (Godot) OAuth PKCE callback URIs for the external test application.

UPDATE application
SET config = jsonb_set(
    config,
    '{redirectUris}',
    (
        SELECT to_jsonb(ARRAY(
            SELECT DISTINCT uri
            FROM unnest(
                COALESCE(
                    ARRAY(SELECT jsonb_array_elements_text(config->'redirectUris')),
                    ARRAY[]::text[]
                )
                || ARRAY[
                    'http://127.0.0.1:8790/oauth/callback',
                    'http://localhost:8790/oauth/callback'
                ]
            ) AS uri
        ))
    ),
    true
),
updated_at = now()
WHERE id = '22222222-2222-2222-2222-222222222299';
